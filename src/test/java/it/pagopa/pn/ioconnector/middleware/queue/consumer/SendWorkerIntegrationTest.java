package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.localstack.LocalStackTestConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.service.DataVaultService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(LocalStackTestConfig.class)
class SendWorkerIntegrationTest {

    @Autowired
    private SendWorker sendWorker;

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
    private DataVaultService dataVaultService;

    @MockitoBean
    private EventBridgeProducer eventBridgeProducer;

    private final Acknowledgement acknowledgement = mock(Acknowledgement.class);

    private String queueUrl;

    @BeforeEach
    void setup() {
        queueUrl = sqsClient.getQueueUrl(r -> r.queueName(config.getSqsSendQueueName())).queueUrl();
        sqsClient.purgeQueue(r -> r.queueUrl(queueUrl));
        when(dataVaultService.deanonymize(anyString())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void longRetry_firstAttempt_updatesDbWithRetryStep1() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-001");
        IOConnectorRequestEntity entity = buildEntity("INT-RETRY-001", null);
        dao.save(entity);

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(eq(request), eq("test-api-key")))
                .thenThrow(new PnHttpResponseException("Service Unavailable", 503));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> updated = dao.findById("INT-RETRY-001");
        assertThat(updated).isPresent();
        assertThat(updated.get().getRetryStep()).isEqualTo(1);
        assertThat(updated.get().getLastRetryTimestamp()).isNotNull();

        verify(eventBridgeProducer, never()).publish(any());
    }

    @Test
    void longRetry_intermediateAttempt_updatesDbWithRetryStep3() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-002");
        IOConnectorRequestEntity entity = buildEntity("INT-RETRY-002", 2);
        dao.save(entity);

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(eq(request), eq("test-api-key")))
                .thenThrow(new PnHttpResponseException("Service Unavailable", 503));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> updated = dao.findById("INT-RETRY-002");
        assertThat(updated).isPresent();
        assertThat(updated.get().getRetryStep()).isEqualTo(3);
        assertThat(updated.get().getLastRetryTimestamp()).isNotNull();

        verify(eventBridgeProducer, never()).publish(any());
    }

    @Test
    void longRetry_exhausted_updatesDbWithRetryExhaustedStatusAndNoEventBridge() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-003");
        IOConnectorRequestEntity entity = buildEntity("INT-RETRY-003", 4);
        dao.save(entity);

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(eq(request), eq("test-api-key")))
                .thenThrow(new PnHttpResponseException("Service Unavailable", 503));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> updated = dao.findById("INT-RETRY-003");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(EventType.IO_SEND_RETRY_EXHAUSTED.name());

        verify(eventBridgeProducer, never()).publish(any());
    }

    @Test
    void nonRetryable_4xx_propagatesExceptionWithoutDbUpdate() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-004");
        IOConnectorRequestEntity entity = buildEntity("INT-RETRY-004", 0);
        dao.save(entity);

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(eq(request), eq("test-api-key")))
                .thenThrow(new PnHttpResponseException("Bad Request", 400));

        assertThatThrownBy(() -> sendWorker.process(message, acknowledgement))
                .isInstanceOf(PnHttpResponseException.class)
                .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(400));

        Optional<IOConnectorRequestEntity> fromDb = dao.findById("INT-RETRY-004");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getRetryStep()).isEqualTo(0);
        assertThat(fromDb.get().getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void longRetry_firstAttempt_429_updatesDbWithRetryStep1() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-005");
        IOConnectorRequestEntity entity = buildEntity("INT-RETRY-005", null);
        dao.save(entity);

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(eq(request), eq("test-api-key")))
                .thenThrow(new PnHttpResponseException("Too Many Requests", 429));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> updated = dao.findById("INT-RETRY-005");
        assertThat(updated).isPresent();
        assertThat(updated.get().getRetryStep()).isEqualTo(1);
        assertThat(updated.get().getLastRetryTimestamp()).isNotNull();

        verify(eventBridgeProducer, never()).publish(any());
    }

    private LimitedProfile buildAllowedProfile() {
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        return profile;
    }

    private MessageSendRequest buildRequest(String requestId) {
        return MessageSendRequest.builder()
                .requestId(requestId)
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-INT-001")
                .recipientTaxId("RSSMRA80A01H501U")
                .senderServiceId("SVC-INT-001")
                .subject("Integration Test Subject")
                .markdown("Integration test body")
                .pollingMaxDate(Instant.now().plusSeconds(3600))
                .build();
    }

    private IOConnectorRequestEntity buildEntity(String requestId, Integer retryStep) {
        return IOConnectorRequestEntity.builder()
                .requestId(requestId)
                .iun("IUN-INT-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .senderServiceId("SVC-INT-001")
                .subject("Integration Test Subject")
                .markdown("Integration test body")
                .status("ACCEPTED")
                .retryStep(retryStep)
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();
    }

    private String enqueueAndReceive(MessageSendRequest request) throws Exception {
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

    private Message<MessageSendRequest> buildMessage(MessageSendRequest request, String receiptHandle) {
        return MessageBuilder.withPayload(request)
                .setHeader("Sqs_ReceiptHandle", receiptHandle)
                .build();
    }
}
