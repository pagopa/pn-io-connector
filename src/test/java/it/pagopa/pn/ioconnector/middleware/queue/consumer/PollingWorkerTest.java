package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.service.io.IOService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollingWorkerTest {

    @Mock private IOService ioService;
    @Mock private IOConnectorRequestDao dao;
    @Mock private EventBridgeProducer eventBridgeProducer;
    @Mock private SqsClient sqsClient;
    @Mock private PnIoConnectorConfig config;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks private PollingWorker pollingWorker;

    @Test
    void withinInterval_changesVisibility_andReturns() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 1, false, 3600, null);
        Message<OutcomePollingRequest> message = buildMessage(request, 10);

        mockQueueUrl();

        pollingWorker.process(message);

        ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(captor.capture());
        assertThat(captor.getValue().visibilityTimeout()).isBetween(3585, 3595);

        verify(ioService, never()).getMessageStatus(any(), any(), any(), any(), any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void withinInterval_cappedByPollingMaxDate() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 1, false, 3600,
                Instant.now().plusSeconds(60));
        Message<OutcomePollingRequest> message = buildMessage(request, 10);

        mockQueueUrl();

        pollingWorker.process(message);

        ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(captor.capture());
        assertThat(captor.getValue().visibilityTimeout()).isBetween(55, 65);
    }

    @Test
    void withinInterval_cappedAt43200() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 1, false, 50000, null);
        Message<OutcomePollingRequest> message = buildMessage(request, 10);

        mockQueueUrl();

        pollingWorker.process(message);

        ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(captor.capture());
        assertThat(captor.getValue().visibilityTimeout()).isEqualTo(43200);
    }

    @Test
    void intervalExpired_proceedsToStatusCheck() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 1, false, 3600,
                Instant.now().plusSeconds(86400));
        Message<OutcomePollingRequest> message = buildMessage(request, 3700);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any())).thenReturn(null);
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message);

        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(ioService).getMessageStatus(any(), any(), any(), any(), any());
    }

    @Test
    void pollingMaxDateExpired_logsAndReturns() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 0, false, 3600,
                Instant.now().minusSeconds(10));
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        pollingWorker.process(message);

        verify(ioService, never()).getServiceUseKey(any());
        verify(ioService, never()).getMessageStatus(any(), any(), any(), any(), any());
        verify(dao, never()).update(any());
        verify(eventBridgeProducer, never()).publish(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void zeroAttempt_statusNull_reEnqueues() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any())).thenReturn(null);
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message);

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        verify(eventBridgeProducer, never()).publish(any());
        verify(dao, never()).update(any());
    }

    @Test
    void statusUnchanged_reEnqueues() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.SENT_TO_IO).build());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message);

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        verify(eventBridgeProducer, never()).publish(any());
        verify(dao, never()).update(any());
    }

    @Test
    void statusChanged_notifiable_publishesAndUpdatesDbAndReEnqueues() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.DELIVERED_TO_USER).build());
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message);

        ArgumentCaptor<OutcomeEvent> eventCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.DELIVERED_TO_USER);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.DELIVERED_TO_USER.name());

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void statusChanged_READ_noPayment_isFinalState() {
        OutcomePollingRequest request = buildRequest(EventType.DELIVERED_TO_USER, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.READ).build());
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());

        pollingWorker.process(message);

        verify(eventBridgeProducer).publish(any());
        verify(dao).update(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void statusChanged_READ_withPayment_notFinalState() {
        OutcomePollingRequest request = buildRequest(EventType.DELIVERED_TO_USER, 0, true);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.READ).build());
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message);

        verify(eventBridgeProducer).publish(any());
        verify(dao).update(any());
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void statusChanged_PAID_withPayment_isFinalState() {
        OutcomePollingRequest request = buildRequest(EventType.READ, 0, true);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.PAID).build());
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());

        pollingWorker.process(message);

        verify(eventBridgeProducer).publish(any());
        verify(dao).update(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void reEnqueue_incrementsAttemptCount_andSetsNewStatus() throws Exception {
        // elapsedSeconds=3700 > pollingIntervalSeconds=3600: bypassa il visibility check
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 2, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 3700);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.DELIVERED_TO_USER).build());
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message);

        ArgumentCaptor<SendMessageRequest> sqsCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sqsCaptor.capture());
        OutcomePollingRequest requeued = objectMapper.readValue(
                sqsCaptor.getValue().messageBody(), OutcomePollingRequest.class);
        assertThat(requeued.getAttemptCount()).isEqualTo(3);
        assertThat(requeued.getLastKnownStatus()).isEqualTo(EventType.DELIVERED_TO_USER);
    }

    @Test
    void dbUpdate_appendsEventToExistingList() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .eventList(List.of(
                        IOConnectorRequestEntity.Event.builder()
                                .eventDate("2024-01-01T00:00:00Z")
                                .status(EventType.SENT_TO_IO.name())
                                .build()
                ))
                .build();

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.DELIVERED_TO_USER).build());
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.of(existing));
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(2);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.SENT_TO_IO.name());
        assertThat(entityCaptor.getValue().getEventList().get(1).getStatus()).isEqualTo(EventType.DELIVERED_TO_USER.name());
    }

    @Test
    void dbUpdate_startsEmptyListIfNoPriorEvents() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getMessageStatus(any(), any(), any(), any(), any()))
                .thenReturn(OutcomeEvent.builder().eventType(EventType.DELIVERED_TO_USER).build());
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.DELIVERED_TO_USER.name());
    }

    private void mockQueueUrl() {
        when(config.getSqsPollingQueueName()).thenReturn("pn-io-connector-polling-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/polling-queue").build());
    }

    private OutcomePollingRequest buildRequest(EventType lastKnownStatus, int attemptCount, boolean paymentData) {
        return buildRequestWith(lastKnownStatus, attemptCount, paymentData, 3600, Instant.now().plusSeconds(86400));
    }

    private OutcomePollingRequest buildRequestWith(EventType lastKnownStatus, int attemptCount, boolean paymentData,
            long pollingIntervalSeconds, Instant pollingMaxDate) {
        return OutcomePollingRequest.builder()
                .requestId("REQ-POLL-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("RSSMRA80A01H501U")
                .ioMessageId("IO-MSG-001")
                .senderServiceId("SVC-001")
                .paymentData(paymentData)
                .lastKnownStatus(lastKnownStatus)
                .pollingMaxDate(pollingMaxDate)
                .pollingIntervalSeconds(pollingIntervalSeconds)
                .attemptCount(attemptCount)
                .build();
    }

    private Message<OutcomePollingRequest> buildMessage(OutcomePollingRequest request, long elapsedSeconds) {
        long sentTimestamp = Instant.now().minusSeconds(elapsedSeconds).toEpochMilli();
        return MessageBuilder.withPayload(request)
                .setHeader("Sqs_ReceiptHandle", "test-receipt-handle")
                .setHeader("SentTimestamp", sentTimestamp)
                .build();
    }
}
