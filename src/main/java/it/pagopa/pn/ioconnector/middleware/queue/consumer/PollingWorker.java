package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.service.io.IOService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static it.pagopa.pn.commons.exceptions.PnExceptionsCodes.ERROR_CODE_PN_GENERIC_ERROR;

@Component
@CustomLog
@RequiredArgsConstructor
public class PollingWorker {

    private final IOService ioService;
    private final IOConnectorRequestDao dao;
    private final EventBridgeProducer eventBridgeProducer;
    private final SqsClient sqsClient;
    private final PnIoConnectorConfig config;
    private final ObjectMapper objectMapper;

    @SqsListener(value = "${pn.io-connector.sqs-polling-queue-name}")
    public void process(Message<OutcomePollingRequest> message) {
        OutcomePollingRequest request = message.getPayload();
        String receiptHandle = (String) message.getHeaders().get("Sqs_ReceiptHandle");
        Instant now = Instant.now();

        if (request.getAttemptCount() > 0) {
            Instant sentTimestamp = request.getEnqueuedAt() != null
                    ? Instant.ofEpochMilli(request.getEnqueuedAt())
                    : Instant.now();
            long elapsed = Duration.between(sentTimestamp, now).getSeconds();
            long remainingInterval = request.getPollingIntervalSeconds() - elapsed;
            if (remainingInterval > 0) {
                long remainingToExpiry = request.getPollingMaxDate() != null
                        ? Duration.between(now, request.getPollingMaxDate()).getSeconds()
                        : remainingInterval;
                int effectiveWait = (int) Math.min(Math.min(remainingInterval, remainingToExpiry), 43200);
                if (effectiveWait > 0) {
                    changePollingQueueVisibility(receiptHandle, effectiveWait);
                    return;
                }
            }
        }

        if (request.getPollingMaxDate() != null && now.isAfter(request.getPollingMaxDate())) {
            log.warn("Polling exhausted requestId={} lastKnownStatus={} iun={}",
                    request.getRequestId(), request.getLastKnownStatus(), request.getIun());
            return;
        }

        String apiKey = ioService.getServiceUseKey(request.getSenderServiceId());
        OutcomeEvent statusResponse = ioService.getMessageStatus(request.getRequestId(),
                request.getXPagopaIoConCxId(), request.getRecipientTaxId(), request.getIoMessageId(), apiKey);

        if (statusResponse == null || statusResponse.getEventType() == request.getLastKnownStatus()) {
            reEnqueue(request, request.getLastKnownStatus());
            return;
        }

        EventType newStatus = statusResponse.getEventType();

        if (newStatus.isNotify()) {
            eventBridgeProducer.publish(OutcomeEvent.builder()
                    .requestId(request.getRequestId())
                    .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                    .ioMessageId(request.getIoMessageId())
                    .eventType(newStatus)
                    .eventTimestamp(now)
                    .build());
        }

        List<IOConnectorRequestEntity.Event> allEvents = new ArrayList<>();
        dao.findByIdConsistentRead(request.getRequestId()).ifPresent(e -> {
            if (e.getEventList() != null) allEvents.addAll(e.getEventList());
        });
        allEvents.add(IOConnectorRequestEntity.Event.builder()
                .eventDate(now.toString())
                .status(newStatus.name())
                .build());
        dao.update(IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .status(newStatus.name())
                .eventList(allEvents)
                .build());

        if (isFinalState(newStatus, request.isPaymentData())) {
            return;
        }

        reEnqueue(request, newStatus);
    }

    private boolean isFinalState(EventType status, boolean hasPaymentData) {
        return (!hasPaymentData && status == EventType.READ)
                || (hasPaymentData && status == EventType.PAID);
    }

    private void reEnqueue(OutcomePollingRequest request, EventType lastKnownStatus) {
        OutcomePollingRequest next = OutcomePollingRequest.builder()
                .requestId(request.getRequestId())
                .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                .iun(request.getIun())
                .recipientTaxId(request.getRecipientTaxId())
                .ioMessageId(request.getIoMessageId())
                .senderServiceId(request.getSenderServiceId())
                .paymentData(request.isPaymentData())
                .lastKnownStatus(lastKnownStatus)
                .pollingMaxDate(request.getPollingMaxDate())
                .pollingIntervalSeconds(request.getPollingIntervalSeconds())
                .attemptCount(request.getAttemptCount() + 1)
                .enqueuedAt(Instant.now().toEpochMilli())
                .build();
        try {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(config.getSqsPollingQueueName())
                    .build()).queueUrl();
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(next))
                    .build());
        } catch (JsonProcessingException e) {
            throw new PnInternalException("Failed to serialize OutcomePollingRequest", ERROR_CODE_PN_GENERIC_ERROR, e);
        }
    }

    private void changePollingQueueVisibility(String receiptHandle, int seconds) {
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(config.getSqsPollingQueueName())
                .build()).queueUrl();
        sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .visibilityTimeout(seconds)
                .build());
    }
}
