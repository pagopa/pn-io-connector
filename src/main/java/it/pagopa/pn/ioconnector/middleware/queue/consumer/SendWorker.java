package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnDataVaultException;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.service.DataVaultService;
import it.pagopa.pn.ioconnector.service.io.IOService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@CustomLog
@RequiredArgsConstructor
public class SendWorker {

    private final IOService ioService;
    private final DataVaultService dataVaultService;
    private final IOConnectorRequestDao dao;
    private final EventBridgeProducer eventBridgeProducer;
    private final SqsClient sqsClient;
    private final PnIoConnectorConfig config;

    @SqsListener(value = "${pn.io-connector.sqs-send-queue-name}")
    public void process(Message<MessageSendRequest> message) {
        MessageSendRequest request = message.getPayload();
        String receiptHandle = (String) message.getHeaders().get("Sqs_ReceiptHandle");

        String apiKey = ioService.getServiceUseKey(request.getSenderServiceId());

        if (!isAttachmentFormatValid(request)) {
            handleInvalidAttachmentFormat(request);
            return;
        }

        String ioMessageId;
        try {
            String taxId = dataVaultService.deanonymize(request.getRecipientTaxId());
            LimitedProfile profile = ioService.checkUserProfile(taxId, apiKey);

            if (!Boolean.TRUE.equals(profile.getSenderAllowed())) {
                handleSenderNotAllowed(request);
                return;
            }
            MessageSendRequest requestToSend = message.getPayload();
            requestToSend.setRecipientTaxId(taxId);
            ioMessageId = ioService.sendMessage(requestToSend, apiKey);
        } catch (PnHttpResponseException | PnDataVaultException ex) {
            int statusCode = ex.getProblem().getStatus();
            if (!isRetryable(statusCode)) {
                throw ex;
            }
            IOConnectorRequestEntity entity = dao.findById(request.getRequestId()).orElse(null);
            int currentStep = (entity != null && entity.getRetryStep() != null) ? entity.getRetryStep() : 0;

            List<Integer> policy = config.getSendRetryPolicy();
            if (currentStep >= policy.size()) {
                handleRetryExhausted(request);
                return;
            }

            int delaySeconds = policy.get(currentStep) * 60;
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
            return;
        }

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
                    .eventType(EventType.SENT_TO_IO)
                    .eventTimestamp(Instant.now())
                    .build();
            eventBridgeProducer.publish(outcomeEvent);
        }

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
                        && a.getFileKey().toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    private void handleInvalidAttachmentFormat(MessageSendRequest request) {
        log.error("Invalid attachment format for requestId={}", request.getRequestId());
        dao.update(IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .status(EventType.ATTACHMENTS_VALIDATION_FAILED.name())
                .eventList(appendEvent(request.getRequestId(), EventType.ATTACHMENTS_VALIDATION_FAILED))
                .build());

        if (EventType.ATTACHMENTS_VALIDATION_FAILED.isNotify()) {
            OutcomeEvent outcomeEvent = OutcomeEvent.builder()
                    .requestId(request.getRequestId())
                    .xPagopaIoConCxId(request.getXPagopaIoConCxId())
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
        dao.update(IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .status(EventType.IO_SEND_RETRY_EXHAUSTED.name())
                .eventList(appendEvent(request.getRequestId(), EventType.IO_SEND_RETRY_EXHAUSTED))
                .build());
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
                .eventType(EventType.SENDER_NOT_ALLOWED)
                .eventTimestamp(Instant.now())
                .build();
        eventBridgeProducer.publish(outcomeEvent);
    }
}
