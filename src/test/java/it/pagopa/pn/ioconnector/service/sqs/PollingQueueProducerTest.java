package it.pagopa.pn.ioconnector.service.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollingQueueProducerTest {

    @Mock private SqsClient sqsClient;
    @Mock private PnIoConnectorConfig config;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks private PollingQueueProducer pollingQueueProducer;

    @Test
    void publish_serializesAndSendsToCorrectQueue() {
        when(config.getSqsPollingQueueName()).thenReturn("pn-io-connector-polling-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/pn-io-connector-polling-queue").build());
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("MSG-001").build());

        OutcomePollingRequest request = OutcomePollingRequest.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .ioMessageId("IO-MSG-001")
                .lastKnownStatus(EventType.SENT_TO_IO)
                .paymentData(true)
                .pollingMaxDate(Instant.now().plusSeconds(3600))
                .build();

        pollingQueueProducer.publish(request);

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());

        SendMessageRequest sent = captor.getValue();
        assertThat(sent.queueUrl()).isEqualTo("https://sqs/pn-io-connector-polling-queue");
        assertThat(sent.messageBody()).contains("REQ-001");
        assertThat(sent.messageBody()).contains("IO-MSG-001");
    }
}
