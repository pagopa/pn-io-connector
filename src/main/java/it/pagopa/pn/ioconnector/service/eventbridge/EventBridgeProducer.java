package it.pagopa.pn.ioconnector.service.eventbridge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;

import static it.pagopa.pn.commons.exceptions.PnExceptionsCodes.ERROR_CODE_PN_GENERIC_ERROR;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.time.Instant;

@Component
@CustomLog
@RequiredArgsConstructor
public class EventBridgeProducer {

    private static final String OUTCOME_EVENT_DETAIL_TYPE = "IoConnectorOutcomeEvent";

    private final EventBridgeClient eventBridgeClient;
    private final PnIoConnectorConfig config;
    private final ObjectMapper objectMapper;

    public void publish(OutcomeEvent event) {
        Instant eventTime = event.getEventTimestamp() != null ? event.getEventTimestamp() : Instant.now();
        String detail;
        try {
            detail = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new PnInternalException("Failed to serialize OutcomeEvent", ERROR_CODE_PN_GENERIC_ERROR, e);
        }
        PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                .eventBusName(config.getEventBridgeBusName())
                .source("pn-io-connector")
                .detailType(OUTCOME_EVENT_DETAIL_TYPE)
                .detail(detail)
                .time(eventTime)
                .build();
        PutEventsRequest putEventsRequest = PutEventsRequest.builder()
                .entries(entry)
                .build();
        log.info("Publishing to EventBridge bus={} source={} detailType={} requestId={} eventType={}",
                entry.eventBusName(), entry.source(), entry.detailType(),
                event.getRequestId(), event.getEventType());
        PutEventsResponse response = eventBridgeClient.putEvents(putEventsRequest);
        PutEventsResultEntry resultEntry = response != null && response.hasEntries() && !response.entries().isEmpty()
                ? response.entries().get(0)
                : null;
        boolean failedEntries = response != null && response.failedEntryCount() != null && response.failedEntryCount() > 0;
        if (failedEntries || (resultEntry != null && resultEntry.errorCode() != null)) {
            log.error("Failed to publish to EventBridge errorCode={} errorMessage={} requestId={} eventType={}",
                    resultEntry != null ? resultEntry.errorCode() : null,
                    resultEntry != null ? resultEntry.errorMessage() : null,
                    event.getRequestId(), event.getEventType());
            return;
        }
        String eventId = resultEntry != null ? resultEntry.eventId() : null;
        log.info("Published to EventBridge eventId={} requestId={} ioMessageId={} eventType={}",
                eventId, event.getRequestId(), event.getIoMessageId(), event.getEventType());
    }
}
