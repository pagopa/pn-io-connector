package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.localstack.LocalStackTestConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.service.io.IOService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(LocalStackTestConfig.class)
class PollingWorkerIntegrationTest {

    @Autowired
    private PollingWorker pollingWorker;

    @Autowired
    private IOConnectorRequestDao dao;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private PnIoConnectorConfig config;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IOService ioService;

    @MockitoBean
    private EventBridgeProducer eventBridgeProducer;

    private final Acknowledgement acknowledgement = mock(Acknowledgement.class);

    private String queueUrl;

    @BeforeEach
    void setup() {
        queueUrl = sqsClient.getQueueUrl(r -> r.queueName(config.getSqsPollingQueueName())).queueUrl();
        sqsClient.purgeQueue(r -> r.queueUrl(queueUrl));
    }

    @Test
    void statusUnchanged_reEnqueuesMessageToPollingQueue() throws Exception {
        OutcomePollingRequest request = buildRequest("INT-POLL-001", EventType.SENT_TO_IO, false);
        String receiptHandle = enqueueAndReceive(request);
        Message<OutcomePollingRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(anyString())).thenReturn("test-api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any())).thenReturn(null);

        pollingWorker.process(message, acknowledgement);

        ReceiveMessageResponse queued = sqsClient.receiveMessage(r -> r
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5));
        assertThat(queued.messages()).isNotEmpty();
        OutcomePollingRequest requeued = objectMapper.readValue(
                queued.messages().get(0).body(), OutcomePollingRequest.class);
        assertThat(requeued.getRequestId()).isEqualTo("INT-POLL-001");
        assertThat(requeued.getLastKnownStatus()).isEqualTo(EventType.SENT_TO_IO);
        assertThat(requeued.getAttemptCount()).isEqualTo(1);

        verify(eventBridgeProducer, never()).publish(any());
    }

    @Test
    void statusChanged_READ_noPayment_updatesDbAndStops() throws Exception {
        IOConnectorRequestEntity entity = buildEntity("INT-POLL-002", EventType.DELIVERED_TO_USER);
        dao.save(entity);

        OutcomePollingRequest request = buildRequest("INT-POLL-002", EventType.DELIVERED_TO_USER, false);
        String receiptHandle = enqueueAndReceive(request);
        Message<OutcomePollingRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(anyString())).thenReturn("test-api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.READ).build());

        pollingWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> updated = dao.findById("INT-POLL-002");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(EventType.READ.name());
        assertThat(updated.get().getEventList()).hasSize(1);
        assertThat(updated.get().getEventList().get(0).getStatus()).isEqualTo(EventType.READ.name());

        verify(eventBridgeProducer).publish(any());

        ReceiveMessageResponse queued = sqsClient.receiveMessage(r -> r
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(1));
        assertThat(queued.messages()).isEmpty();
    }

    @Test
    void statusChanged_PAID_withPayment_updatesDbAndStops() throws Exception {
        IOConnectorRequestEntity entity = buildEntity("INT-POLL-003", EventType.READ);
        dao.save(entity);

        OutcomePollingRequest request = buildRequest("INT-POLL-003", EventType.READ, true);
        String receiptHandle = enqueueAndReceive(request);
        Message<OutcomePollingRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(anyString())).thenReturn("test-api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.PAID).build());

        pollingWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> updated = dao.findById("INT-POLL-003");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(EventType.PAID.name());

        verify(eventBridgeProducer).publish(any());

        ReceiveMessageResponse queued = sqsClient.receiveMessage(r -> r
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(1));
        assertThat(queued.messages()).isEmpty();
    }

    @Test
    void statusChanged_intermediate_updatesDbAndReEnqueues() throws Exception {
        IOConnectorRequestEntity entity = buildEntity("INT-POLL-004", EventType.SENT_TO_IO);
        dao.save(entity);

        OutcomePollingRequest request = buildRequest("INT-POLL-004", EventType.SENT_TO_IO, false);
        String receiptHandle = enqueueAndReceive(request);
        Message<OutcomePollingRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(anyString())).thenReturn("test-api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.DELIVERED_TO_USER).build());

        pollingWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> updated = dao.findById("INT-POLL-004");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(EventType.DELIVERED_TO_USER.name());

        verify(eventBridgeProducer).publish(any());

        ReceiveMessageResponse queued = sqsClient.receiveMessage(r -> r
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5));
        assertThat(queued.messages()).isNotEmpty();
        OutcomePollingRequest requeued = objectMapper.readValue(
                queued.messages().get(0).body(), OutcomePollingRequest.class);
        assertThat(requeued.getLastKnownStatus()).isEqualTo(EventType.DELIVERED_TO_USER);
        assertThat(requeued.getAttemptCount()).isEqualTo(1);
    }

    // ---- Helpers ----

    private OutcomePollingRequest buildRequest(String requestId, EventType lastKnownStatus, boolean paymentData) {
        return OutcomePollingRequest.builder()
                .requestId(requestId)
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-INT-001")
                .recipientTaxId("RSSMRA80A01H501U")
                .ioMessageId("IO-MSG-INT-001")
                .senderServiceId("SVC-INT-001")
                .paymentData(paymentData)
                .lastKnownStatus(lastKnownStatus)
                .pollingMaxDate(Instant.now().plusSeconds(86400))
                .pollingIntervalSeconds(3600)
                .attemptCount(0)
                .build();
    }

    private IOConnectorRequestEntity buildEntity(String requestId, EventType status) {
        return IOConnectorRequestEntity.builder()
                .requestId(requestId)
                .iun("IUN-INT-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .senderServiceId("SVC-INT-001")
                .ioMessageId("IO-MSG-INT-001")
                .status(status.name())
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();
    }

    private String enqueueAndReceive(OutcomePollingRequest request) throws Exception {
        String messageBody = objectMapper.writeValueAsString(request);
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build());

        ReceiveMessageResponse received = sqsClient.receiveMessage(r -> r
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5));

        assertThat(received.messages()).isNotEmpty();
        return received.messages().get(0).receiptHandle();
    }

    private Message<OutcomePollingRequest> buildMessage(OutcomePollingRequest request, String receiptHandle) {
        return MessageBuilder.withPayload(request)
                .setHeader("Sqs_ReceiptHandle", receiptHandle)
                .setHeader("SentTimestamp", 0L)
                .build();
    }
}
