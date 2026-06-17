package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
    public void process(Message<OutcomePollingRequest> message, Acknowledgement acknowledgement) {
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
                    // Non ack: il messaggio rimane in flight e torna visibile dopo il visibility timeout
                    return;
                }
            }
        }

        if (request.getPollingMaxDate() != null && now.isAfter(request.getPollingMaxDate())) {
            log.warn("Polling exhausted for requestId={}, lastKnownStatus is {}", request.getRequestId(), request.getLastKnownStatus());
            acknowledgement.acknowledge();
            return;
        }

        log.info("Executing polling for requestId={}, lastKnownStatus is {}", request.getRequestId(), request.getLastKnownStatus());

        String apiKey = ioService.getServiceUseKey(request.getSenderServiceId());

        // Calcolo i nuovi stati rilevati da IO
        Set<EventType> reached = ioService.getReachedEventTypes(
                request.getRecipientTaxId(), request.getIoMessageId(), apiKey);

        // Recupero gli stati già elaborati
        List<IOConnectorRequestEntity.Event> allEvents = new ArrayList<>();
        dao.findByIdConsistentRead(request.getRequestId()).ifPresent(e -> {
            if (e.getEventList() != null) allEvents.addAll(e.getEventList());
        });
        EnumSet<EventType> alreadyRecorded = toEventTypeSet(allEvents);

        // Rimuovo eventuali stati nuovamente rilevati che sono già stati registrati
        EnumSet<EventType> newEvents = EnumSet.noneOf(EventType.class);
        newEvents.addAll(reached);
        newEvents.removeAll(alreadyRecorded);

        if (newEvents.isEmpty()) {
            // Nessun nuovo evento, proseguo col polling
            reEnqueue(request, request.getLastKnownStatus());
            acknowledgement.acknowledge();
            return;
        }

        // Notifico i nuovi stati e li aggiungo alla eventList
        String nowStr = now.toString();
        for (EventType ev : newEvents) {
            if (ev.isNotify()) {
                eventBridgeProducer.publish(OutcomeEvent.builder()
                        .requestId(request.getRequestId())
                        .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                        .ioMessageId(request.getIoMessageId())
                        .eventType(ev)
                        .eventTimestamp(now)
                        .build());
            }
            allEvents.add(IOConnectorRequestEntity.Event.builder()
                    .eventDate(nowStr)
                    .status(ev.name())
                    .build());
        }

        // Setto lo stato più "avanzato" nel campo status
        EventType denormalizedStatus = highestRank(allEvents, request.getLastKnownStatus());
        dao.update(IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .status(denormalizedStatus.name())
                .eventList(allEvents)
                .build());

        if (isFinalState(request.isPaymentData(), allEvents)) {
            acknowledgement.acknowledge();
            log.info("Polling ended with final state {} for requestId={}", denormalizedStatus, request.getRequestId());
            return;
        }

        reEnqueue(request, denormalizedStatus);
        acknowledgement.acknowledge();
    }

    private boolean isFinalState(boolean hasPaymentData, List<IOConnectorRequestEntity.Event> allEvents) {
        if (contains(allEvents, EventType.IO_DELIVERY_FAILED)) {
            return true;
        }
        boolean hasRead = contains(allEvents, EventType.READ);
        if (!hasPaymentData) {
            return hasRead;
        }
        return hasRead && contains(allEvents, EventType.PAID);
    }

    private boolean contains(List<IOConnectorRequestEntity.Event> events, EventType eventType) {
        return events.stream().anyMatch(e -> eventType.name().equals(e.getStatus()));
    }

    private EnumSet<EventType> toEventTypeSet(List<IOConnectorRequestEntity.Event> events) {
        EnumSet<EventType> set = EnumSet.noneOf(EventType.class);
        for (IOConnectorRequestEntity.Event e : events) {
            try {
                set.add(EventType.valueOf(e.getStatus()));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // Eventuali EventType non riconosciuti vanno ignorati
            }
        }
        return set;
    }

    private EventType highestRank(List<IOConnectorRequestEntity.Event> events, EventType fallback) {
        EventType highest = fallback;
        for (IOConnectorRequestEntity.Event e : events) {
            EventType t;
            try {
                t = EventType.valueOf(e.getStatus());
            } catch (IllegalArgumentException | NullPointerException ignored) {
                continue;
            }
            if (highest == null || t.getProgressionRank() > highest.getProgressionRank()) {
                highest = t;
            }
        }
        return highest;
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
