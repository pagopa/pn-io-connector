package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnDataVaultException;
import it.pagopa.pn.ioconnector.exceptions.PnIoGetProfileException;
import it.pagopa.pn.ioconnector.localstack.LocalStackTestConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.service.DataVaultService;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.service.io.IOService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.List;
import java.util.Optional;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.utils.IoConnectorMDCUtils;
import it.pagopa.pn.ioconnector.utils.MdcTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeNameForSends;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private SqsTemplate sqsTemplate;

    @MockitoBean
    private IOService ioService;

    @MockitoBean
    private DataVaultService dataVaultService;

    @MockitoBean
    private EventBridgeProducer eventBridgeProducer;

    private final Acknowledgement acknowledgement = mock(Acknowledgement.class);

    private String queueUrl;
    private String pollingQueueUrl;

    @BeforeEach
    void setup() {
        queueUrl = sqsClient.getQueueUrl(r -> r.queueName(config.getSqsSendQueueName())).queueUrl();
        sqsClient.purgeQueue(r -> r.queueUrl(queueUrl));
        pollingQueueUrl = sqsClient.getQueueUrl(r -> r.queueName(config.getSqsPollingQueueName())).queueUrl();
        sqsClient.purgeQueue(r -> r.queueUrl(pollingQueueUrl));
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
    void longRetry_exhausted_updatesDbWithFailedToSendAndPublishesRetryExhausted() throws Exception {
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
        assertThat(updated.get().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(updated.get().getEventList()).hasSize(1);
        assertThat(updated.get().getEventList().get(0).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.FAILED_TO_SEND);
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("Retry Exhausted");
    }

    @Test
    void nonRetryable_4xx_persistsFailedToSendPublishesEventAndAcks() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-004");
        IOConnectorRequestEntity entity = buildEntity("INT-RETRY-004", 0);
        dao.save(entity);

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(eq(request), eq("test-api-key")))
                .thenThrow(new PnHttpResponseException("Bad Request", 400));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> fromDb = dao.findById("INT-RETRY-004");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getRetryStep()).isEqualTo(0);
        assertThat(fromDb.get().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(fromDb.get().getEventList()).hasSize(1);
        assertThat(fromDb.get().getEventList().get(0).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.FAILED_TO_SEND);
        assertThat(outcomeCaptor.getValue().getRequestId()).isEqualTo("INT-RETRY-004");
        assertThat(outcomeCaptor.getValue().getXPagopaIoConCxId()).isEqualTo("pn-delivery-push");
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("400 - Bad Request");

        verify(acknowledgement).acknowledge();
    }

    @Test
    void nonRetryable_checkUserProfile_403_persistsFailedToSend() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-006");
        dao.save(buildEntity("INT-RETRY-006", 0));

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key")))
                .thenThrow(new PnHttpResponseException("Forbidden", 403));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> fromDb = dao.findById("INT-RETRY-006");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(fromDb.get().getEventList()).hasSize(1);
        assertThat(fromDb.get().getEventList().get(0).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        verify(ioService, never()).sendMessage(any(), any());
        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("403 - Forbidden");
        verify(acknowledgement).acknowledge();
    }

    @Test
    void nonRetryable_checkUserProfile_getProfileException_403_persistsFailedToSend() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-009");
        dao.save(buildEntity("INT-RETRY-009", 0));

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key")))
                .thenThrow(new PnIoGetProfileException(403, "Forbidden"));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> fromDb = dao.findById("INT-RETRY-009");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        verify(ioService, never()).sendMessage(any(), any());
        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getErrorDetail())
                .isEqualTo("403 - Errore in fase di POST/profile su IO");
        verify(acknowledgement).acknowledge();
    }

    @Test
    void nonRetryable_deanonymize_400_persistsFailedToSend() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-007");
        dao.save(buildEntity("INT-RETRY-007", 0));

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(dataVaultService.deanonymize(request.getRecipientTaxId()))
                .thenThrow(new PnDataVaultException(400, "Bad Request"));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> fromDb = dao.findById("INT-RETRY-007");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        verify(ioService, never()).checkUserProfile(anyString(), anyString());
        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("400 - Errore chiamata a DataVault");
        verify(acknowledgement).acknowledge();
    }

    @Test
    void failedToSend_appendsEventToExistingEventList() throws Exception {
        MessageSendRequest request = buildRequest("INT-RETRY-008");
        IOConnectorRequestEntity entity = buildEntity("INT-RETRY-008", 0);
        entity.setEventList(List.of(IOConnectorRequestEntity.Event.builder()
                .eventDate(Instant.now().toString())
                .status(EventType.ACCEPTED.name())
                .build()));
        dao.save(entity);

        String receiptHandle = enqueueAndReceive(request);
        Message<MessageSendRequest> message = buildMessage(request, receiptHandle);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(eq(request.getRecipientTaxId()), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(eq(request), eq("test-api-key")))
                .thenThrow(new PnHttpResponseException("Bad Request", 400));

        sendWorker.process(message, acknowledgement);

        Optional<IOConnectorRequestEntity> fromDb = dao.findById("INT-RETRY-008");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getEventList()).hasSize(2);
        assertThat(fromDb.get().getEventList().get(0).getStatus()).isEqualTo(EventType.ACCEPTED.name());
        assertThat(fromDb.get().getEventList().get(1).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(fromDb.get().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        verify(acknowledgement).acknowledge();
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

    private static final String TRACE_HEADER = "Sqs_Msa_AWSTraceHeader";

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    private SendMessageResponse sendWithTrace(String targetQueueUrl, MessageSendRequest request, String trace) throws Exception {
        return sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(targetQueueUrl)
                .messageBody(objectMapper.writeValueAsString(request))
                .messageSystemAttributes(Map.of(MessageSystemAttributeNameForSends.AWS_TRACE_HEADER,
                        MessageSystemAttributeValue.builder().dataType("String").stringValue(trace).build()))
                .build());
    }

    @SuppressWarnings("unchecked")
    private Message<MessageSendRequest> receiveFromSendQueue() {
        Optional<Message<MessageSendRequest>> received = sqsTemplate.receive(
                from -> from.queue(config.getSqsSendQueueName()).pollTimeout(Duration.ofSeconds(5)),
                MessageSendRequest.class);
        assertThat(received).isPresent();
        return received.get();
    }

    private Message<?> receiveFromPollingQueue() {
        Optional<Message<?>> received = sqsTemplate.receive(
                from -> from.queue(config.getSqsPollingQueueName()).pollTimeout(Duration.ofSeconds(5)));
        assertThat(received).isPresent();
        return received.get();
    }

    @Test
    void process_realSqsMessage_setsMdcFromHeaders() throws Exception {
        MessageSendRequest request = buildRequest("INT-MDC-001");
        dao.save(buildEntity("INT-MDC-001", 0));
        SendMessageResponse sent = sendWithTrace(queueUrl, request, MdcTestSupport.XRAY_TRACE);
        Message<MessageSendRequest> message = receiveFromSendQueue();

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(dataVaultService.deanonymize(anyString())).thenAnswer(invocation -> {
            captured.set(MdcTestSupport.snapshot());
            return invocation.getArgument(0);
        });
        when(ioService.checkUserProfile(anyString(), eq("test-api-key"))).thenReturn(buildNotAllowedProfile());

        sendWorker.process(message, acknowledgement);

        assertThat(message.getHeaders()).containsKey(TRACE_HEADER);
        assertThat(captured.get())
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, MdcTestSupport.XRAY_TRACE)
                .containsEntry(MDCUtils.MDC_PN_CTX_MESSAGE_ID, sent.messageId())
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "INT-MDC-001")
                .containsEntry(MDCUtils.MDC_PN_IUN_KEY, "IUN-INT-001")
                .containsEntry(MDCUtils.MDC_CX_ID_KEY, "RSSMRA80A01H501U");
        assertThat(message.getHeaders().getId()).hasToString(sent.messageId());
    }

    @Test
    void publishPollingRequest_realSqs_messageHasAwsTraceHeader() throws Exception {
        MessageSendRequest request = buildRequest("INT-MDC-002");
        dao.save(buildEntity("INT-MDC-002", 0));
        sendWithTrace(queueUrl, request, MdcTestSupport.XRAY_TRACE);
        Message<MessageSendRequest> message = receiveFromSendQueue();

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(anyString(), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(any(), eq("test-api-key"))).thenReturn("IO-MSG-INT-002");

        sendWorker.process(message, acknowledgement);

        Message<?> polling = receiveFromPollingQueue();
        assertThat(polling.getHeaders()).containsEntry(TRACE_HEADER, MdcTestSupport.XRAY_TRACE);
    }

    @Test
    void publishPollingRequest_realSqs_nonXrayTrace_omitsAwsTraceHeader() {
        MessageSendRequest request = buildRequest("INT-MDC-003");
        dao.save(buildEntity("INT-MDC-003", 0));
        Message<MessageSendRequest> message = MessageBuilder.withPayload(request)
                .setHeader("Sqs_ReceiptHandle", "test-receipt-handle")
                .setHeader(TRACE_HEADER, "trace_id:" + UUID.randomUUID())
                .build();

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("test-api-key");
        when(ioService.checkUserProfile(anyString(), eq("test-api-key"))).thenReturn(buildAllowedProfile());
        when(ioService.sendMessage(any(), eq("test-api-key"))).thenReturn("IO-MSG-INT-003");

        sendWorker.process(message, acknowledgement);

        verify(acknowledgement).acknowledge();
        Message<?> polling = receiveFromPollingQueue();
        assertThat(polling.getHeaders()).doesNotContainKey(TRACE_HEADER);
    }

    private LimitedProfile buildNotAllowedProfile() {
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(false);
        return profile;
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
