package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.exceptions.PnDataVaultException;
import it.pagopa.pn.ioconnector.exceptions.PnIoGetProfileException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.service.DataVaultService;
import it.pagopa.pn.ioconnector.service.io.IOService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.utils.IoConnectorMDCUtils;
import it.pagopa.pn.ioconnector.utils.LogEventCaptor;
import it.pagopa.pn.ioconnector.utils.MdcTestSupport;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeNameForSends;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static it.pagopa.pn.ioconnector.utils.LogUtils.CHECK_ATTACHMENTS;
import static it.pagopa.pn.ioconnector.utils.LogUtils.CHECK_SENDER_ALLOWED;
import static it.pagopa.pn.ioconnector.utils.LogUtils.SEND_WORKER_PROCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendWorkerTest {

    @Mock private IOService ioService;
    @Mock private DataVaultService dataVaultService;
    @Mock private IOConnectorRequestDao dao;
    @Mock private EventBridgeProducer eventBridgeProducer;
    @Mock private SqsClient sqsClient;
    @Mock private PnIoConnectorConfig config;
    @Mock private Acknowledgement acknowledgement;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks private SendWorker sendWorker;

    @Test
    void senderNotAllowed_whenProfileReturnsFalse() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(false);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENDER_NOT_ALLOWED.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.SENDER_NOT_ALLOWED.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.SENDER_NOT_ALLOWED);

        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void sendSuccess_updatesDbAndPublishesEvents() throws Exception {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenReturn("IO-MSG-001");
        when(config.getSqsPollingQueueName()).thenReturn("pn-io-connector-polling-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/polling-queue").build());
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENT_TO_IO.name());
        assertThat(entityCaptor.getValue().getIoMessageId()).isEqualTo("IO-MSG-001");
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.SENT_TO_IO.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.SENT_TO_IO);
        assertThat(outcomeCaptor.getValue().getNoticeCode()).isNull();

        ArgumentCaptor<SendMessageRequest> sqsCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sqsCaptor.capture());
        OutcomePollingRequest pollingRequest = objectMapper.readValue(
                sqsCaptor.getValue().messageBody(), OutcomePollingRequest.class);
        assertThat(pollingRequest.getLastKnownStatus()).isEqualTo(EventType.SENT_TO_IO);
        assertThat(pollingRequest.getAttemptCount()).isEqualTo(0);
        assertThat(pollingRequest.getSenderServiceId()).isEqualTo("SVC-001");
        assertThat(pollingRequest.getNoticeCode()).isNull();
        verify(acknowledgement).acknowledge();
    }

    @Test
    void propagatesException_onSendMessageError() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new RuntimeException("IO 500"));

        assertThatThrownBy(() -> sendWorker.process(message, acknowledgement))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("IO 500");

        verify(dao, never()).update(any());
        verify(eventBridgeProducer, never()).publish(any());
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void buildsPollingRequest_withPaymentData() throws Exception {
        MessageSendRequest request = buildRequest();
        request.setPaymentData(MessageSendRequest.PaymentData.builder()
                .amount(100)
                .noticeCode("302000000000000000")
                .creditorTaxId("77777777777")
                .build());
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenReturn("IO-MSG-002");
        when(config.getSqsPollingQueueName()).thenReturn("pn-io-connector-polling-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/polling-queue").build());
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.SENT_TO_IO);
        assertThat(outcomeCaptor.getValue().getNoticeCode()).isEqualTo("302000000000000000");

        ArgumentCaptor<SendMessageRequest> sqsCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sqsCaptor.capture());
        OutcomePollingRequest pollingRequest = objectMapper.readValue(
                sqsCaptor.getValue().messageBody(), OutcomePollingRequest.class);
        assertThat(pollingRequest.isPaymentData()).isTrue();
        assertThat(pollingRequest.getSenderServiceId()).isEqualTo("SVC-001");
        assertThat(pollingRequest.getNoticeCode()).isEqualTo("302000000000000000");
        verify(acknowledgement).acknowledge();
    }

    @Test
    void sendMessage_retryableError_firstAttempt_changesVisibilityWithDelay() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Too Many Requests", 429));

        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .retryStep(null)
                .build();
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(entity));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));
        when(config.getSqsSendQueueName()).thenReturn("pn-io-connector-send-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder()
                        .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789/pn-io-connector-send-queue")
                        .build());

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<ChangeMessageVisibilityRequest> visibilityCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(visibilityCaptor.capture());
        assertThat(visibilityCaptor.getValue().visibilityTimeout()).isEqualTo(300);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRetryStep()).isEqualTo(1);
        assertThat(entityCaptor.getValue().getLastRetryTimestamp()).isNotNull();

        verify(eventBridgeProducer, never()).publish(any());
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void sendMessage_retryableError_lastAttempt_marksFailedToSendAndPublishesRetryExhausted() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Service Unavailable", 503));

        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .retryStep(4)
                .build();
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(entity));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.FAILED_TO_SEND);
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("Retry Exhausted");

        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void sendMessage_retryableError_secondAttempt_changesVisibilityWithIncreasedDelay() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Bad Gateway", 502));

        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .retryStep(1)
                .build();
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(entity));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));
        when(config.getSqsSendQueueName()).thenReturn("pn-io-connector-send-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder()
                        .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789/pn-io-connector-send-queue")
                        .build());

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<ChangeMessageVisibilityRequest> visibilityCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(visibilityCaptor.capture());
        assertThat(visibilityCaptor.getValue().visibilityTimeout()).isEqualTo(600);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRetryStep()).isEqualTo(2);
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void sendMessage_nonRetryableError_404_marksFailedToSendAndAcksMessage() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.FAILED_TO_SEND);
        assertThat(outcomeCaptor.getValue().getRequestId()).isEqualTo("REQ-001");
        assertThat(outcomeCaptor.getValue().getXPagopaIoConCxId()).isEqualTo("pn-delivery-push");
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("404 - Not Found");

        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void sendMessage_checkUserProfile_retryableError_appliesVisibilityTimeout() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key"))
                .thenThrow(new PnHttpResponseException("Too Many Requests", 429));

        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .retryStep(null)
                .build();
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(entity));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));
        when(config.getSqsSendQueueName()).thenReturn("pn-io-connector-send-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder()
                        .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789/pn-io-connector-send-queue")
                        .build());

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<ChangeMessageVisibilityRequest> visibilityCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(visibilityCaptor.capture());
        assertThat(visibilityCaptor.getValue().visibilityTimeout()).isEqualTo(300);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRetryStep()).isEqualTo(1);

        verify(eventBridgeProducer, never()).publish(any());
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void senderNotAllowed_whenProfileReturnsSenderAllowedNull() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(null);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENDER_NOT_ALLOWED.name());
        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void deanonymize_retryableError_429_appliesVisibilityTimeout() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(dataVaultService.deanonymize(TOKEN_TAX_ID))
                .thenThrow(new PnDataVaultException(429, "Too Many Requests"));

        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .retryStep(null)
                .build();
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(entity));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));
        when(config.getSqsSendQueueName()).thenReturn("pn-io-connector-send-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder()
                        .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789/pn-io-connector-send-queue")
                        .build());

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<ChangeMessageVisibilityRequest> visibilityCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(visibilityCaptor.capture());
        assertThat(visibilityCaptor.getValue().visibilityTimeout()).isEqualTo(300);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRetryStep()).isEqualTo(1);

        verify(eventBridgeProducer, never()).publish(any());
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void invalidAttachmentFormat_nonPdfExtension_updatesDbAndPublishesEvent() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().fileKey("documento.docx").build()
        ));
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus())
                .isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());

        if (EventType.ATTACHMENTS_VALIDATION_FAILED.isNotify()) {
            ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
            verify(eventBridgeProducer).publish(outcomeCaptor.capture());
            assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED);
            assertThat(outcomeCaptor.getValue().getRequestId()).isEqualTo(request.getRequestId());
        }

        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void invalidAttachmentFormat_nullFileKey_updatesDbAndPublishesEvent() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().fileKey(null).build()
        ));
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());

        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void duplicateAttachmentIds_updatesDbAndPublishesEvent() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().id("att-1").fileKey("doc1.pdf").name("doc1.pdf").build(),
                MessageSendRequest.Attachment.builder().id("att-1").fileKey("doc2.pdf").name("doc2.pdf").build()
        ));
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus())
                .isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());

        if (EventType.ATTACHMENTS_VALIDATION_FAILED.isNotify()) {
            ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
            verify(eventBridgeProducer).publish(outcomeCaptor.capture());
            assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED);
            assertThat(outcomeCaptor.getValue().getRequestId()).isEqualTo(request.getRequestId());
        }

        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void nullAttachmentId_updatesDbAndPublishesEvent() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().id(null).fileKey("doc.pdf").name("doc.pdf").build()
        ));
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());

        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void blankAttachmentId_updatesDbAndPublishesEvent() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().id("   ").fileKey("doc.pdf").name("doc.pdf").build()
        ));
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());

        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void invalidAttachmentFormat_nameWithoutPdf_updatesDbAndPublishesEvent() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().id("att-1").fileKey("doc.pdf").name("documento").build()
        ));
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());

        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void invalidAttachmentFormat_nullName_updatesDbAndPublishesEvent() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().id("att-1").fileKey("doc.pdf").name(null).build()
        ));
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.ATTACHMENTS_VALIDATION_FAILED.name());

        verify(ioService, never()).sendMessage(any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void validAttachment_caseInsensitivePdfSuffix_isAccepted() throws Exception {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().id("att-1").fileKey("doc.PDF").name("documento.Pdf").build()
        ));
        Message<MessageSendRequest> message = buildMessage(request);
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenReturn("IO-MSG-001");
        when(config.getSqsPollingQueueName()).thenReturn("pn-io-connector-polling-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/polling-queue").build());
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENT_TO_IO.name());
        verify(ioService).sendMessage(eq(request), eq("api-key"));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void deanonymize_nonRetryableError_400_marksFailedToSendAndAcksMessage() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(dataVaultService.deanonymize(TOKEN_TAX_ID))
                .thenThrow(new PnDataVaultException(400, "Bad Request"));

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.FAILED_TO_SEND);
        assertThat(outcomeCaptor.getValue().getRequestId()).isEqualTo("REQ-001");
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("400 - Errore chiamata a DataVault");

        verify(ioService, never()).checkUserProfile(any(), any());
        verify(ioService, never()).sendMessage(any(), any());
        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void checkUserProfile_nonRetryableError_403_marksFailedToSend() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key"))
                .thenThrow(new PnHttpResponseException("Forbidden", 403));

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRequestId()).isEqualTo("REQ-001");
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.FAILED_TO_SEND);
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("403 - Forbidden");

        verify(ioService, never()).sendMessage(any(), any());
        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void checkUserProfile_getProfileException_403_marksFailedToSend() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key"))
                .thenThrow(new PnIoGetProfileException(403, "Forbidden"));

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.FAILED_TO_SEND.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.FAILED_TO_SEND);
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("403 - Errore in fase di POST/profile su IO");

        verify(ioService, never()).sendMessage(any(), any());
        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void sendMessage_nonRetryableError_400_marksFailedToSend_withNoticeCode() {
        MessageSendRequest request = buildRequest();
        request.setPaymentData(MessageSendRequest.PaymentData.builder()
                .amount(100)
                .noticeCode("302000000000000000")
                .creditorTaxId("77777777777")
                .build());
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Bad Request", 400));

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.FAILED_TO_SEND);
        assertThat(outcomeCaptor.getValue().getNoticeCode()).isEqualTo("302000000000000000");
        assertThat(outcomeCaptor.getValue().getIoMessageId()).isNull();
        assertThat(outcomeCaptor.getValue().getEventTimestamp()).isNotNull();
        assertThat(outcomeCaptor.getValue().getErrorDetail()).isEqualTo("400 - Bad Request");

        verify(acknowledgement).acknowledge();
    }

    @Test
    void sendMessage_error_500_isRetryable_doesNotMarkFailedToSend() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .retryStep(null)
                .build();
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(entity));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));
        when(config.getSqsSendQueueName()).thenReturn("pn-io-connector-send-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder()
                        .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789/pn-io-connector-send-queue")
                        .build());

        sendWorker.process(message, acknowledgement);

        ArgumentCaptor<ChangeMessageVisibilityRequest> visibilityCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(visibilityCaptor.capture());
        assertThat(visibilityCaptor.getValue().visibilityTimeout()).isEqualTo(300);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRetryStep()).isEqualTo(1);
        assertThat(entityCaptor.getValue().getStatus()).isNull();

        verify(eventBridgeProducer, never()).publish(any());
        verify(acknowledgement, never()).acknowledge();
    }

    private static final String TOKEN_TAX_ID = "PF-abc123";
    private static final String REAL_TAX_ID = "RSSMRA80A01H501U";

    private MessageSendRequest buildRequest() {
        return MessageSendRequest.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId(TOKEN_TAX_ID)
                .senderServiceId("SVC-001")
                .subject("Test subject")
                .markdown("Test body")
                .pollingMaxDate(Instant.now().plusSeconds(3600))
                .build();
    }

    private Message<MessageSendRequest> buildMessage(MessageSendRequest request) {
        return MessageBuilder.withPayload(request)
                .setHeader("Sqs_ReceiptHandle", "test-receipt-handle")
                .build();
    }

    private static final String TRACE_HEADER = "Sqs_Msa_AWSTraceHeader";

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    private Message<MessageSendRequest> buildMessageWithTrace(MessageSendRequest request) {
        return MessageBuilder.withPayload(request)
                .setHeader("Sqs_ReceiptHandle", "test-receipt-handle")
                .setHeader(TRACE_HEADER, MdcTestSupport.XRAY_TRACE)
                .build();
    }

    private void stubHappyPath(MessageSendRequest request) {
        stubHappyPath(request, invocation -> "IO-MSG-001");
    }

    private void stubHappyPath(MessageSendRequest request, Answer<String> sendMessageAnswer) {
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(new LimitedProfile().senderAllowed(true));
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenAnswer(sendMessageAnswer);
        when(config.getSqsPollingQueueName()).thenReturn("pn-io-connector-polling-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/polling-queue").build());
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());
    }

    private void stubSenderNotAllowed(MessageSendRequest request) {
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(new LimitedProfile().senderAllowed(false));
    }

    private void stubNonRetryableFailure(MessageSendRequest request) {
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(new LimitedProfile().senderAllowed(true));
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Error in call url=https://api.io.pagopa.it/api/v1/messages"
                        + " method=POST statusCode=404 and body=<html>Not Found</html>", 404));
    }

    private static PnHttpResponseException realisticIoException(int status) {
        return new PnHttpResponseException("Error in call url=https://api.io.pagopa.it/api/v1/messages/"
                + REAL_TAX_ID + "/01JC7ZQ3M0 method=GET statusCode=" + status
                + " and body=<html>Bad Gateway</html>", status);
    }

    @Test
    void process_setsMdcFromHeadersAndPayload() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessageWithTrace(request);
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        stubSenderNotAllowed(request);
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenAnswer(MdcTestSupport.capturingInto(captured, REAL_TAX_ID));

        sendWorker.process(message, acknowledgement);

        assertThat(captured.get())
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, MdcTestSupport.XRAY_TRACE)
                .containsEntry(MDCUtils.MDC_PN_CTX_MESSAGE_ID, message.getHeaders().getId().toString())
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-001")
                .containsEntry(MDCUtils.MDC_PN_IUN_KEY, "IUN-001")
                .containsEntry(MDCUtils.MDC_CX_ID_KEY, TOKEN_TAX_ID);
    }

    @Test
    void process_cxIdIsInternalIdNotTaxId() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessageWithTrace(request);
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        stubHappyPath(request, MdcTestSupport.capturingInto(captured, "IO-MSG-001"));

        sendWorker.process(message, acknowledgement);

        assertThat(captured.get()).containsEntry(MDCUtils.MDC_CX_ID_KEY, TOKEN_TAX_ID);
        assertThat(captured.get().values()).doesNotContain(REAL_TAX_ID);
    }

    @Test
    void process_setsIoMessageIdAfterSend() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessageWithTrace(request);
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        stubHappyPath(request);
        doAnswer(invocation -> {
            captured.set(MdcTestSupport.snapshot());
            return null;
        }).when(eventBridgeProducer).publish(any());

        sendWorker.process(message, acknowledgement);

        assertThat(captured.get()).containsEntry(IoConnectorMDCUtils.MDC_IO_MESSAGE_ID_KEY, "IO-MSG-001");
    }

    @Test
    void process_clearsMdc_onSuccess() {
        MDC.put(MDCUtils.MDC_PN_IUN_KEY, "STALE-IUN");
        MessageSendRequest request = buildRequest();
        stubHappyPath(request);

        sendWorker.process(buildMessageWithTrace(request), acknowledgement);

        assertThat(MdcTestSupport.snapshot()).isEmpty();
    }

    @Test
    void process_clearsMdc_onException() {
        MDC.put(MDCUtils.MDC_PN_IUN_KEY, "STALE-IUN");
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessageWithTrace(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(new LimitedProfile().senderAllowed(true));
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenThrow(new RuntimeException("IO 500"));

        assertThatThrownBy(() -> sendWorker.process(message, acknowledgement))
                .isInstanceOf(RuntimeException.class);

        assertThat(MdcTestSupport.snapshot()).isEmpty();
    }

    @Test
    void process_logsStartingAndEndingProcess() {
        MessageSendRequest request = buildRequest();
        stubHappyPath(request);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendWorker.class)) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.INFO, "Starting process", SEND_WORKER_PROCESS)).isTrue();
            assertThat(logs.hasEvent(Level.INFO, "Ending process", SEND_WORKER_PROCESS)).isTrue();
        }
    }

    @Test
    void process_failedToSend_logsEndingProcessWithErrors() {
        MessageSendRequest request = buildRequest();
        stubNonRetryableFailure(request);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendWorker.class)) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.WARN, "Ending process", "with errors=")).isTrue();
        }
    }

    @Test
    void process_invalidAttachments_logsCheckingFailed() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(MessageSendRequest.Attachment.builder().id("att-1").fileKey("documento.docx").build()));
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendWorker.class)) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.WARN, "Checking", CHECK_ATTACHMENTS, "failed reason=")).isTrue();
        }
    }

    @Test
    void process_validAttachments_logsCheckingPassed() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(MessageSendRequest.Attachment.builder()
                .id("att-1").name("document.pdf").fileKey("safestorage://document.pdf").build()));
        stubSenderNotAllowed(request);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendWorker.class)) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.INFO, "Checking", CHECK_ATTACHMENTS, "passed")).isTrue();
        }
    }

    @Test
    void process_senderNotAllowed_logsCheckingFailed() {
        MessageSendRequest request = buildRequest();
        stubSenderNotAllowed(request);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendWorker.class)) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.WARN, "Checking", CHECK_SENDER_ALLOWED, "failed reason=")).isTrue();
        }
    }

    @Test
    void sendMessage_retryableError_logsWarnWithStepAndDelay() {
        MessageSendRequest request = buildRequest();
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(new LimitedProfile().senderAllowed(true));
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Too Many Requests", 429));
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(
                IOConnectorRequestEntity.builder().requestId(request.getRequestId()).retryStep(null).build()));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));
        when(config.getSqsSendQueueName()).thenReturn("pn-io-connector-send-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/send-queue").build());

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendWorker.class)) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.WARN, "step=", "delay=")).isTrue();
            assertThat(logs.hasEvent(Level.WARN, "Ending process", "with errors=", "retry scheduled step=0 statusCode=429")).isTrue();
        }
    }

    @Test
    void sendMessage_nonRetryableError_logsWarnOnlyFailedToSend() {
        MessageSendRequest request = buildRequest();
        stubNonRetryableFailure(request);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendWorker.class)) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.WARN, "statusCode=404")).isTrue();
            assertThat(logs.hasEvent(Level.ERROR, "Error while trying to send message to IO")).isFalse();
        }
    }

    @Test
    void publishPollingRequest_setsRecipientInternalIdFromTokenAndRecipientTaxIdFromDeanonymized() throws Exception {
        MessageSendRequest request = buildRequest();
        stubHappyPath(request);

        sendWorker.process(buildMessageWithTrace(request), acknowledgement);

        ArgumentCaptor<SendMessageRequest> sqsCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sqsCaptor.capture());
        OutcomePollingRequest pollingRequest = objectMapper.readValue(
                sqsCaptor.getValue().messageBody(), OutcomePollingRequest.class);
        assertThat(pollingRequest.getRecipientInternalId()).isEqualTo(TOKEN_TAX_ID);
        assertThat(pollingRequest.getRecipientTaxId()).isEqualTo(REAL_TAX_ID);
    }

    @Test
    void publishPollingRequest_setsAwsTraceHeaderFromMdc() {
        MessageSendRequest request = buildRequest();
        stubHappyPath(request);

        sendWorker.process(buildMessageWithTrace(request), acknowledgement);

        ArgumentCaptor<SendMessageRequest> sqsCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sqsCaptor.capture());
        assertThat(sqsCaptor.getValue().messageSystemAttributes())
                .containsKey(MessageSystemAttributeNameForSends.AWS_TRACE_HEADER);
        assertThat(sqsCaptor.getValue().messageSystemAttributes()
                .get(MessageSystemAttributeNameForSends.AWS_TRACE_HEADER).stringValue())
                .isEqualTo(MdcTestSupport.XRAY_TRACE);
    }

    @Test
    void publishPollingRequest_logsSqsInsertion() {
        MessageSendRequest request = buildRequest();
        stubHappyPath(request);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendWorker.class)) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.DEBUG, "Inserting data")).isTrue();
            assertThat(logs.hasEvent(Level.INFO, "Inserted data in SQS pn-io-connector-polling-queue")).isTrue();
        }
    }

    @Test
    void process_neverLogsTaxIdInClear() {
        MessageSendRequest request = buildRequest();
        stubHappyPath(request);

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
        }
    }

    @Test
    void process_failedToSend_neverLogsTaxIdInClear() {
        MessageSendRequest request = buildRequest();
        stubNonRetryableFailure(request);

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void process_failedToSend_logsExceptionClassAndStatusInsteadOfMessage() {
        MessageSendRequest request = buildRequest();
        stubNonRetryableFailure(request);

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.WARN, "statusCode=404", "PnHttpResponseException")).isTrue();
            assertThat(logs.hasEvent(Level.WARN, "Ending process", "with errors=", "PnHttpResponseException")).isTrue();
        }
    }

    @Test
    void sendMessage_retryableError_neverLogsTaxIdInClear() {
        MessageSendRequest request = buildRequest();
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(new LimitedProfile().senderAllowed(true));
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenThrow(realisticIoException(502));
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(
                IOConnectorRequestEntity.builder().requestId(request.getRequestId()).retryStep(null).build()));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));
        when(config.getSqsSendQueueName()).thenReturn("pn-io-connector-send-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/send-queue").build());

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            sendWorker.process(buildMessageWithTrace(request), acknowledgement);

            assertThat(logs.hasEvent(Level.WARN, "step=", "delay=", "502")).isTrue();
            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
        }
    }

    @Test
    void process_unexpectedException_neverLogsTaxIdInClear() {
        MessageSendRequest request = buildRequest();
        when(dataVaultService.deanonymize(TOKEN_TAX_ID)).thenReturn(REAL_TAX_ID);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(REAL_TAX_ID, "api-key")).thenReturn(new LimitedProfile().senderAllowed(true));
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new IllegalStateException("unexpected failure for " + REAL_TAX_ID));
        Message<MessageSendRequest> message = buildMessageWithTrace(request);

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            assertThatThrownBy(() -> sendWorker.process(message, acknowledgement))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(logs.hasEvent(Level.WARN, "Ending process", "with errors=", "IllegalStateException")).isTrue();
            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }
}
