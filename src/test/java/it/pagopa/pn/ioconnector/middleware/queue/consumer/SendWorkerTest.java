package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.service.sqs.PollingQueueProducer;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.service.io.IOService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendWorkerTest {

    @Mock private IOService ioService;
    @Mock private IOConnectorRequestDao dao;
    @Mock private EventBridgeProducer eventBridgeProducer;
    @Mock private PollingQueueProducer pollingQueueProducer;
    @Mock private SqsClient sqsClient;
    @Mock private PnIoConnectorConfig config;

    @InjectMocks private SendWorker sendWorker;

    @Test
    void senderNotAllowed_whenProfileReturnsFalse() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(false);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);

        sendWorker.process(message);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENDER_NOT_ALLOWED.name());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.SENDER_NOT_ALLOWED.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.SENDER_NOT_ALLOWED);

        verify(ioService, never()).sendMessage(any(), any());
        verify(pollingQueueProducer, never()).publish(any());
    }

    @Test
    void sendSuccess_updatesDbAndPublishesEvents() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenReturn("IO-MSG-001");

        sendWorker.process(message);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENT_TO_IO.name());
        assertThat(entityCaptor.getValue().getIoMessageId()).isEqualTo("IO-MSG-001");
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.SENT_TO_IO.name());

        ArgumentCaptor<OutcomeEvent> outcomeCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue().getEventType()).isEqualTo(EventType.SENT_TO_IO);

        verify(pollingQueueProducer).publish(any(OutcomePollingRequest.class));
    }

    @Test
    void propagatesException_onSendMessageError() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);
        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new RuntimeException("IO 500"));

        assertThatThrownBy(() -> sendWorker.process(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("IO 500");

        verify(dao, never()).update(any());
        verify(eventBridgeProducer, never()).publish(any());
    }

    @Test
    void buildsPollingRequest_withPaymentData() {
        MessageSendRequest request = buildRequest();
        request.setPaymentData(MessageSendRequest.PaymentData.builder()
                .amount(100)
                .noticeCode("302000000000000000")
                .creditorTaxId("77777777777")
                .build());
        Message<MessageSendRequest> message = buildMessage(request);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenReturn("IO-MSG-002");

        sendWorker.process(message);

        ArgumentCaptor<OutcomePollingRequest> pollingCaptor = ArgumentCaptor.forClass(OutcomePollingRequest.class);
        verify(pollingQueueProducer).publish(pollingCaptor.capture());
        assertThat(pollingCaptor.getValue().isPaymentData()).isTrue();
    }

    @Test
    void sendMessage_retryableError_firstAttempt_changesVisibilityWithDelay() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
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

        sendWorker.process(message);

        ArgumentCaptor<ChangeMessageVisibilityRequest> visibilityCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(visibilityCaptor.capture());
        assertThat(visibilityCaptor.getValue().visibilityTimeout()).isEqualTo(300);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRetryStep()).isEqualTo(1);
        assertThat(entityCaptor.getValue().getLastRetryTimestamp()).isNotNull();

        verify(eventBridgeProducer, never()).publish(any());
    }

    @Test
    void sendMessage_retryableError_lastAttempt_callsHandleRetryExhausted() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Service Unavailable", 503));

        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .retryStep(4)
                .build();
        when(dao.findById(request.getRequestId())).thenReturn(Optional.of(entity));
        when(config.getSendRetryPolicy()).thenReturn(List.of(5, 10, 20, 40));

        sendWorker.process(message);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.IO_SEND_RETRY_EXHAUSTED.name());

        verify(eventBridgeProducer, never()).publish(any());
        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    void sendMessage_retryableError_secondAttempt_changesVisibilityWithIncreasedDelay() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
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

        sendWorker.process(message);

        ArgumentCaptor<ChangeMessageVisibilityRequest> visibilityCaptor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(visibilityCaptor.capture());
        assertThat(visibilityCaptor.getValue().visibilityTimeout()).isEqualTo(600);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRetryStep()).isEqualTo(2);
    }

    @Test
    void sendMessage_nonRetryableError_404_propagatesExceptionWithoutVisibilityChange() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        assertThatThrownBy(() -> sendWorker.process(message))
                .isInstanceOf(PnHttpResponseException.class)
                .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(404));

        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(dao, never()).update(any());
        verify(eventBridgeProducer, never()).publish(any());
    }

    @Test
    void sendMessage_nonRetryableError_400_propagatesExceptionWithoutVisibilityChange() {
        MessageSendRequest request = buildRequest();
        Message<MessageSendRequest> message = buildMessage(request);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new PnHttpResponseException("Bad Request", 400));

        assertThatThrownBy(() -> sendWorker.process(message))
                .isInstanceOf(PnHttpResponseException.class)
                .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(400));

        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(dao, never()).update(any());
        verify(eventBridgeProducer, never()).publish(any());
    }

    private MessageSendRequest buildRequest() {
        return MessageSendRequest.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("RSSMRA80A01H501U")
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
}
