package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnDataVaultException;
import it.pagopa.pn.ioconnector.exceptions.PnIoGetProfileException;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.service.DataVaultService;
import it.pagopa.pn.ioconnector.service.io.IOService;
import it.pagopa.pn.ioconnector.utils.IoConnectorMDCUtils;
import it.pagopa.pn.ioconnector.utils.LogUtils;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static it.pagopa.pn.commons.exceptions.PnExceptionsCodes.ERROR_CODE_PN_GENERIC_ERROR;
import static it.pagopa.pn.ioconnector.utils.LogUtils.CHECK_ATTACHMENTS;
import static it.pagopa.pn.ioconnector.utils.LogUtils.CHECK_SENDER_ALLOWED;
import static it.pagopa.pn.ioconnector.utils.LogUtils.SEND_WORKER_PROCESS;

@Component
@CustomLog
@RequiredArgsConstructor
public class SendWorker {

    private static final String RETRY_EXHAUSTED_ERROR_DETAIL = "Retry Exhausted";

    private final IOService ioService;
    private final DataVaultService dataVaultService;
    private final IOConnectorRequestDao dao;
    private final EventBridgeProducer eventBridgeProducer;
    private final SqsClient sqsClient;
    private final PnIoConnectorConfig config;
    private final ObjectMapper objectMapper;

    @SqsListener(value = "${pn.io-connector.sqs-send-queue-name}")
    public void process(Message<MessageSendRequest> message, Acknowledgement acknowledgement) {
        MessageSendRequest request = message.getPayload();
        UUID messageId = message.getHeaders().getId();
        IoConnectorMDCUtils.initContext(
                message.getHeaders().get(SqsHeaders.MessageSystemAttributes.SQS_AWS_TRACE_HEADER, String.class),
                messageId != null ? messageId.toString() : null,
                request.getRequestId(),
                request.getIun(),
                request.getRecipientTaxId(),
                null);
        log.logStartingProcess(SEND_WORKER_PROCESS);
        try {
            handleMessage(message, request, acknowledgement);
        } catch (RuntimeException e) {
            log.logEndingProcess(SEND_WORKER_PROCESS, false, LogUtils.describeError(e), null);
            throw e;
        } finally {
            IoConnectorMDCUtils.clear();
        }
    }

    private void handleMessage(Message<MessageSendRequest> message, MessageSendRequest request, Acknowledgement acknowledgement) {
        String receiptHandle = (String) message.getHeaders().get("Sqs_ReceiptHandle");
        String recipientInternalId = request.getRecipientTaxId();

        String apiKey = ioService.getServiceUseKey(request.getSenderServiceId());

        log.logChecking(CHECK_ATTACHMENTS);
        if (!isAttachmentFormatValid(request) || !hasUniqueAttachmentIds(request)) {
            handleInvalidAttachmentFormat(request);
            acknowledgement.acknowledge();
            log.logEndingProcess(SEND_WORKER_PROCESS);
            return;
        }
        log.logCheckingOutcome(CHECK_ATTACHMENTS, true);

        String ioMessageId;
        try {
            String taxId = dataVaultService.deanonymize(request.getRecipientTaxId());
            LimitedProfile profile = ioService.checkUserProfile(taxId, apiKey);

            log.logChecking(CHECK_SENDER_ALLOWED);
            if (!Boolean.TRUE.equals(profile.getSenderAllowed())) {
                handleSenderNotAllowed(request);
                acknowledgement.acknowledge();
                log.logEndingProcess(SEND_WORKER_PROCESS);
                return;
            }
            log.logCheckingOutcome(CHECK_SENDER_ALLOWED, true);
            MessageSendRequest requestToSend = message.getPayload();
            requestToSend.setRecipientTaxId(taxId);
            ioMessageId = ioService.sendMessage(requestToSend, apiKey);
        } catch (PnHttpResponseException | PnDataVaultException | PnIoGetProfileException ex) {
            int statusCode = resolveStatusCode(ex);
            if (!isRetryable(statusCode)) {
                handleFailedToSend(request, statusCode, ex);
                acknowledgement.acknowledge();
                log.logEndingProcess(SEND_WORKER_PROCESS, false, statusCode + " - " + ex.getClass().getSimpleName(), null);
                return;
            }
            IOConnectorRequestEntity entity = dao.findById(request.getRequestId()).orElse(null);
            int currentStep = (entity != null && entity.getRetryStep() != null) ? entity.getRetryStep() : 0;

            List<Integer> policy = config.getSendRetryPolicy();
            if (currentStep >= policy.size()) {
                handleRetryExhausted(request);
                acknowledgement.acknowledge();
                log.logEndingProcess(SEND_WORKER_PROCESS, false, RETRY_EXHAUSTED_ERROR_DETAIL, null);
                return;
            }

            int delaySeconds = policy.get(currentStep) * 60;
            log.warn("Retryable error sending message to IO for requestId={} statusCode={} step={} delay={}s",
                    request.getRequestId(), statusCode, currentStep, delaySeconds);
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(config.getSqsSendQueueName())
                    .build()).queueUrl();
            sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .visibilityTimeout(delaySeconds)
                    .build());

            dao.update(IOConnectorRequestEntity.builder()
                    .requestId(request.getRequestId())
                    .retryStep(currentStep + 1)
                    .lastRetryTimestamp(Instant.now().toString())
                    .build());
            // Non ack: il messaggio rimane in flight e torna visibile dopo il visibility timeout
            log.logEndingProcess(SEND_WORKER_PROCESS, false, "retry scheduled step=" + currentStep + " statusCode=" + statusCode, null);
            return;
        }
        IoConnectorMDCUtils.putIoMessageId(ioMessageId);

        dao.update(IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .ioMessageId(ioMessageId)
                .status(EventType.SENT_TO_IO.name())
                .eventList(appendEvent(request.getRequestId(), EventType.SENT_TO_IO))
                .build());

        if (EventType.SENT_TO_IO.isNotify()) {
            OutcomeEvent outcomeEvent = OutcomeEvent.builder()
                    .requestId(request.getRequestId())
                    .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                    .ioMessageId(ioMessageId)
                    .noticeCode(extractNoticeCode(request))
                    .eventType(EventType.SENT_TO_IO)
                    .eventTimestamp(Instant.now())
                    .build();
            eventBridgeProducer.publish(outcomeEvent);
        }

        publishPollingRequest(request, ioMessageId, recipientInternalId);
        acknowledgement.acknowledge();
        log.logEndingProcess(SEND_WORKER_PROCESS);
    }

    private void publishPollingRequest(MessageSendRequest request, String ioMessageId, String recipientInternalId) {
        Instant now = Instant.now();
        Instant pollingMaxDate = request.getPollingMaxDate() != null
                ? request.getPollingMaxDate()
                : now.plus(Duration.ofMinutes(config.getPollingIntervalMins()));
        long pollingIntervalSeconds;
        if (config.getPollingFixedIntervalSeconds() != null && config.getPollingFixedIntervalSeconds() > 0) {
            pollingIntervalSeconds = config.getPollingFixedIntervalSeconds();
        } else {
            long windowSeconds = Duration.between(now, pollingMaxDate).getSeconds();
            pollingIntervalSeconds = Math.max(30, Math.min(21600, windowSeconds / 4));
        }

        OutcomePollingRequest pollingRequest = OutcomePollingRequest.builder()
                .requestId(request.getRequestId())
                .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                .iun(request.getIun())
                .recipientTaxId(request.getRecipientTaxId())
                .recipientInternalId(recipientInternalId)
                .ioMessageId(ioMessageId)
                .senderServiceId(request.getSenderServiceId())
                .paymentData(request.getPaymentData() != null)
                .noticeCode(extractNoticeCode(request))
                .lastKnownStatus(EventType.SENT_TO_IO)
                .pollingMaxDate(pollingMaxDate)
                .pollingIntervalSeconds(pollingIntervalSeconds)
                .attemptCount(0)
                .enqueuedAt(now.toEpochMilli())
                .build();

        try {
            String queueName = config.getSqsPollingQueueName();
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build()).queueUrl();
            log.debug("Inserting data {} in SQS {}", pollingRequest, queueName);
            sqsClient.sendMessage(IoConnectorMDCUtils.withAwsTraceHeader(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(pollingRequest)))
                    .build());
            log.info("Inserted data in SQS {}", queueName);
            log.info("Polling initialized for requestId={} until {} with interval={}",
                    pollingRequest.getRequestId(), pollingMaxDate, pollingRequest.getPollingIntervalSeconds());
        } catch (JsonProcessingException e) {
            throw new PnInternalException("Failed to serialize OutcomePollingRequest", ERROR_CODE_PN_GENERIC_ERROR, e);
        }
    }

    private String extractNoticeCode(MessageSendRequest request) {
        return request.getPaymentData() != null ? request.getPaymentData().getNoticeCode() : null;
    }

    private List<IOConnectorRequestEntity.Event> appendEvent(String requestId, EventType eventType) {
        List<IOConnectorRequestEntity.Event> events = new ArrayList<>();
        dao.findByIdConsistentRead(requestId).ifPresent(e -> {
            if (e.getEventList() != null) events.addAll(e.getEventList());
        });
        events.add(IOConnectorRequestEntity.Event.builder()
                .eventDate(Instant.now().toString())
                .status(eventType.name())
                .build());
        return events;
    }

    private boolean isAttachmentFormatValid(MessageSendRequest request) {
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) return true;
        return request.getAttachments().stream()
                .allMatch(a -> a.getFileKey() != null
                        && a.getFileKey().toLowerCase(Locale.ROOT).endsWith(".pdf")
                        && a.getName() != null
                        && a.getName().toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    private boolean hasUniqueAttachmentIds(MessageSendRequest request) {
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) return true;
        List<String> ids = request.getAttachments().stream()
                .map(MessageSendRequest.Attachment::getId)
                .toList();
        if (ids.stream().anyMatch(id -> !StringUtils.hasText(id))) return false;
        return ids.stream().distinct().count() == ids.size();
    }

    private void handleInvalidAttachmentFormat(MessageSendRequest request) {
        log.logCheckingOutcome(CHECK_ATTACHMENTS, false,
                "invalid attachment format for requestId=" + request.getRequestId());
        dao.update(IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .status(EventType.ATTACHMENTS_VALIDATION_FAILED.name())
                .eventList(appendEvent(request.getRequestId(), EventType.ATTACHMENTS_VALIDATION_FAILED))
                .build());

        if (EventType.ATTACHMENTS_VALIDATION_FAILED.isNotify()) {
            OutcomeEvent outcomeEvent = OutcomeEvent.builder()
                    .requestId(request.getRequestId())
                    .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                    .noticeCode(extractNoticeCode(request))
                    .eventType(EventType.ATTACHMENTS_VALIDATION_FAILED)
                    .eventTimestamp(Instant.now())
                    .build();
            eventBridgeProducer.publish(outcomeEvent);
        }
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void handleRetryExhausted(MessageSendRequest request) {
        log.error("Long retry exhausted for requestId={}", request.getRequestId());
        markFailedToSend(request, RETRY_EXHAUSTED_ERROR_DETAIL);
    }

    private void handleSenderNotAllowed(MessageSendRequest request) {
        dao.update(IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .status(EventType.SENDER_NOT_ALLOWED.name())
                .eventList(appendEvent(request.getRequestId(), EventType.SENDER_NOT_ALLOWED))
                .build());

        OutcomeEvent outcomeEvent = OutcomeEvent.builder()
                .requestId(request.getRequestId())
                .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                .noticeCode(extractNoticeCode(request))
                .eventType(EventType.SENDER_NOT_ALLOWED)
                .eventTimestamp(Instant.now())
                .build();
        eventBridgeProducer.publish(outcomeEvent);

        log.logCheckingOutcome(CHECK_SENDER_ALLOWED, false,
                "sender not allowed for requestId=" + request.getRequestId() + " iun=" + request.getIun());
    }

    private void handleFailedToSend(MessageSendRequest request, int statusCode, PnRuntimeException ex) {
        log.warn("Error while trying to send message to IO for requestId={} iun={} statusCode={} detail={}",
                request.getRequestId(), request.getIun(), statusCode, ex.getClass().getSimpleName());
        markFailedToSend(request, buildErrorDetail(statusCode, ex));
    }

    private int resolveStatusCode(PnRuntimeException ex) {
        Integer status = ex.getProblem() != null ? ex.getProblem().getStatus() : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String buildErrorDetail(int statusCode, PnRuntimeException ex) {
        String description = ex.getProblem() != null && StringUtils.hasText(ex.getProblem().getDetail())
                ? ex.getProblem().getDetail()
                : ex.getMessage();
        return statusCode + " - " + description;
    }

    private void markFailedToSend(MessageSendRequest request, String errorDetail) {
        dao.update(IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .status(EventType.FAILED_TO_SEND.name())
                .eventList(appendEvent(request.getRequestId(), EventType.FAILED_TO_SEND))
                .build());

        if (EventType.FAILED_TO_SEND.isNotify()) {
            OutcomeEvent outcomeEvent = OutcomeEvent.builder()
                    .requestId(request.getRequestId())
                    .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                    .noticeCode(extractNoticeCode(request))
                    .eventType(EventType.FAILED_TO_SEND)
                    .eventTimestamp(Instant.now())
                    .errorDetail(errorDetail)
                    .build();
            eventBridgeProducer.publish(outcomeEvent);
        }
    }
}
