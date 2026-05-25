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

import java.time.Instant;

@Component
@CustomLog
@RequiredArgsConstructor
public class EventBridgeProducer {

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
                .detailType(event.getEventType() != null ? event.getEventType().name() : "UNKNOWN")
                .detail(detail)
                .time(eventTime)
                .build();
        PutEventsRequest putEventsRequest = PutEventsRequest.builder()
                .entries(entry)
                .build();
        eventBridgeClient.putEvents(putEventsRequest);
    }
}
