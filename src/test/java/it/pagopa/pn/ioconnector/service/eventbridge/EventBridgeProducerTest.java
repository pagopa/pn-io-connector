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
}
