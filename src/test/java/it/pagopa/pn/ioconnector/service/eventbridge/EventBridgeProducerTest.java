package it.pagopa.pn.ioconnector.service.eventbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import ch.qos.logback.classic.Level;
import it.pagopa.pn.ioconnector.utils.LogEventCaptor;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventBridgeProducerTest {

    @Mock private EventBridgeClient eventBridgeClient;
    @Mock private PnIoConnectorConfig config;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks private EventBridgeProducer eventBridgeProducer;

    @Test
    void publish_invokesEventBridgeWithCorrectParams() {
        when(config.getEventBridgeBusName()).thenReturn("pn-io-connector-bus");
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(PutEventsResponse.builder().failedEntryCount(0).build());

        OutcomeEvent event = OutcomeEvent.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .ioMessageId("IO-MSG-001")
                .eventType(EventType.SENT_TO_IO)
                .build();

        eventBridgeProducer.publish(event);

        ArgumentCaptor<PutEventsRequest> captor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(captor.capture());

        PutEventsRequest request = captor.getValue();
        assertThat(request.entries()).hasSize(1);
        String detail = request.entries().get(0).detail();
        assertThat(detail).contains("REQ-001");
        assertThat(detail).contains("SENT_TO_IO");
        assertThat(request.entries().get(0).eventBusName()).isEqualTo("pn-io-connector-bus");
        assertThat(request.entries().get(0).detailType()).isEqualTo("IoConnectorOutcomeEvent");
    }

    @Test
    void publish_serializesErrorDetail() {
        when(config.getEventBridgeBusName()).thenReturn("pn-io-connector-bus");
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(PutEventsResponse.builder().failedEntryCount(0).build());

        OutcomeEvent event = OutcomeEvent.builder()
                .requestId("REQ-002")
                .xPagopaIoConCxId("pn-delivery-push")
                .eventType(EventType.FAILED_TO_SEND)
                .errorDetail("404 - Not Found")
                .build();

        eventBridgeProducer.publish(event);

        ArgumentCaptor<PutEventsRequest> captor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(eventBridgeClient).putEvents(captor.capture());

        String detail = captor.getValue().entries().get(0).detail();
        assertThat(detail).contains("FAILED_TO_SEND");
        assertThat(detail).contains("\"errorDetail\":\"404 - Not Found\"");
    }

    @Test
    void publish_logsIoMessageId() {
        when(config.getEventBridgeBusName()).thenReturn("pn-io-connector-bus");
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(PutEventsResponse.builder().failedEntryCount(0).build());

        try (LogEventCaptor logs = LogEventCaptor.attachTo(EventBridgeProducer.class)) {
            eventBridgeProducer.publish(OutcomeEvent.builder()
                    .requestId("REQ-001")
                    .ioMessageId("IO-MSG-001")
                    .eventType(EventType.SENT_TO_IO)
                    .build());

            assertThat(logs.anyMessageContains("IO-MSG-001")).isTrue();
        }
    }

    @Test
    void publish_successfulResponse_logsPublishedAndNoError() {
        when(config.getEventBridgeBusName()).thenReturn("pn-io-connector-bus");
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(PutEventsResponse.builder()
                        .failedEntryCount(0)
                        .entries(PutEventsResultEntry.builder().eventId("evt-1").build())
                        .build());

        try (LogEventCaptor logs = LogEventCaptor.attachTo(EventBridgeProducer.class)) {
            eventBridgeProducer.publish(OutcomeEvent.builder()
                    .requestId("REQ-001")
                    .eventType(EventType.SENT_TO_IO)
                    .build());

            assertThat(logs.hasEvent(Level.INFO, "Published to EventBridge", "evt-1")).isTrue();
            assertThat(logs.events()).noneMatch(e -> e.getLevel() == Level.ERROR);
        }
    }

    @Test
    void publish_failedEntry_logsErrorAndNoPublishedInfo() {
        when(config.getEventBridgeBusName()).thenReturn("pn-io-connector-bus");
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(PutEventsResponse.builder()
                        .failedEntryCount(1)
                        .entries(PutEventsResultEntry.builder()
                                .errorCode("InternalFailure")
                                .errorMessage("The request processing has failed")
                                .build())
                        .build());

        try (LogEventCaptor logs = LogEventCaptor.attachTo(EventBridgeProducer.class)) {
            eventBridgeProducer.publish(OutcomeEvent.builder()
                    .requestId("REQ-001")
                    .eventType(EventType.SENT_TO_IO)
                    .build());

            assertThat(logs.hasEvent(Level.ERROR, "Failed to publish to EventBridge",
                    "errorCode=InternalFailure", "errorMessage=The request processing has failed",
                    "requestId=REQ-001", "eventType=SENT_TO_IO")).isTrue();
            assertThat(logs.anyMessageContains("Published to EventBridge")).isFalse();
        }
    }

    @Test
    void publish_entryWithErrorCodeAndZeroFailedCount_logsErrorAndNoPublishedInfo() {
        when(config.getEventBridgeBusName()).thenReturn("pn-io-connector-bus");
        when(eventBridgeClient.putEvents(any(PutEventsRequest.class)))
                .thenReturn(PutEventsResponse.builder()
                        .failedEntryCount(0)
                        .entries(PutEventsResultEntry.builder()
                                .errorCode("ThrottlingException")
                                .errorMessage("Rate exceeded")
                                .build())
                        .build());

        try (LogEventCaptor logs = LogEventCaptor.attachTo(EventBridgeProducer.class)) {
            eventBridgeProducer.publish(OutcomeEvent.builder()
                    .requestId("REQ-001")
                    .eventType(EventType.SENT_TO_IO)
                    .build());

            assertThat(logs.hasEvent(Level.ERROR, "Failed to publish to EventBridge",
                    "errorCode=ThrottlingException", "errorMessage=Rate exceeded")).isTrue();
            assertThat(logs.anyMessageContains("Published to EventBridge")).isFalse();
        }
    }
}
