package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollingWorkerTest {

    @Mock private IOService ioService;
    @Mock private IOConnectorRequestDao dao;
    @Mock private EventBridgeProducer eventBridgeProducer;
    @Mock private SqsClient sqsClient;
    @Mock private PnIoConnectorConfig config;
    @Mock private Acknowledgement acknowledgement;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks private PollingWorker pollingWorker;

    @Test
    void withinInterval_changesVisibility_andReturns() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 1, false, 3600, null);
        Message<OutcomePollingRequest> message = buildMessage(request, 10);

        mockQueueUrl();

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(captor.capture());
        assertThat(captor.getValue().visibilityTimeout()).isBetween(3585, 3595);

        verify(ioService, never()).getReachedEventTypes(any(), any(), any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void withinInterval_cappedByPollingMaxDate() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 1, false, 3600,
                Instant.now().plusSeconds(60));
        Message<OutcomePollingRequest> message = buildMessage(request, 10);

        mockQueueUrl();

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(captor.capture());
        assertThat(captor.getValue().visibilityTimeout()).isBetween(55, 65);
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void withinInterval_cappedAt43200() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 1, false, 50000, null);
        Message<OutcomePollingRequest> message = buildMessage(request, 10);

        mockQueueUrl();

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(captor.capture());
        assertThat(captor.getValue().visibilityTimeout()).isEqualTo(43200);
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void intervalExpired_proceedsToStatusCheck() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 1, false, 3600,
                Instant.now().plusSeconds(86400));
        Message<OutcomePollingRequest> message = buildMessage(request, 3700);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        verify(ioService).getReachedEventTypes(any(), any(), any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void pollingMaxDateExpired_logsAndReturns() {
        OutcomePollingRequest request = buildRequestWith(EventType.SENT_TO_IO, 0, false, 3600,
                Instant.now().minusSeconds(10));
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        pollingWorker.process(message, acknowledgement);

        verify(ioService, never()).getServiceUseKey(any());
        verify(ioService, never()).getReachedEventTypes(any(), any(), any());
        verify(dao, never()).update(any());
        verify(eventBridgeProducer, never()).publish(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void emptyReached_reEnqueues() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        verify(eventBridgeProducer, never()).publish(any());
        verify(dao, never()).update(any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void noNewEvents_reEnqueues() {
        OutcomePollingRequest request = buildRequest(EventType.DELIVERED_TO_USER, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of(EventType.DELIVERED_TO_USER));
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.SENT_TO_IO, EventType.DELIVERED_TO_USER)));
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        verify(eventBridgeProducer, never()).publish(any());
        verify(dao, never()).update(any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void statusChanged_notifiable_publishesAndUpdatesDbAndReEnqueues() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of(EventType.DELIVERED_TO_USER));
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<OutcomeEvent> eventCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.DELIVERED_TO_USER);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.DELIVERED_TO_USER.name());

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void statusChanged_READ_noPayment_isFinalState() {
        OutcomePollingRequest request = buildRequest(EventType.DELIVERED_TO_USER, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any()))
                .thenReturn(Set.of(EventType.DELIVERED_TO_USER, EventType.READ));
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.SENT_TO_IO, EventType.DELIVERED_TO_USER)));

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<OutcomeEvent> eventCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.READ);
        verify(dao).update(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void statusChanged_READ_withPayment_notFinalState() {
        OutcomePollingRequest request = buildRequest(EventType.DELIVERED_TO_USER, 0, true);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any()))
                .thenReturn(Set.of(EventType.DELIVERED_TO_USER, EventType.READ));
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.SENT_TO_IO, EventType.DELIVERED_TO_USER)));
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        verify(eventBridgeProducer).publish(any());
        verify(dao).update(any());
        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void statusChanged_PAID_withPayment_isFinalState() {
        OutcomePollingRequest request = buildRequest(EventType.READ, 0, true);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any()))
                .thenReturn(Set.of(EventType.DELIVERED_TO_USER, EventType.READ, EventType.PAID));
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.SENT_TO_IO, EventType.DELIVERED_TO_USER, EventType.READ)));

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<OutcomeEvent> eventCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.PAID);
        verify(dao).update(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
        verify(acknowledgement).acknowledge();
    }

    @Test
    void reEnqueue_incrementsAttemptCount_andSetsNewStatus() throws Exception {
        // elapsedSeconds=3700 > pollingIntervalSeconds=3600: bypassa il visibility check
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 2, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 3700);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of(EventType.DELIVERED_TO_USER));
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<SendMessageRequest> sqsCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sqsCaptor.capture());
        OutcomePollingRequest requeued = objectMapper.readValue(
                sqsCaptor.getValue().messageBody(), OutcomePollingRequest.class);
        assertThat(requeued.getAttemptCount()).isEqualTo(3);
        assertThat(requeued.getLastKnownStatus()).isEqualTo(EventType.DELIVERED_TO_USER);
        verify(acknowledgement).acknowledge();
    }

    @Test
    void dbUpdate_appendsEventToExistingList() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of(EventType.DELIVERED_TO_USER));
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.SENT_TO_IO)));
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(2);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.SENT_TO_IO.name());
        assertThat(entityCaptor.getValue().getEventList().get(1).getStatus()).isEqualTo(EventType.DELIVERED_TO_USER.name());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void dbUpdate_startsEmptyListIfNoPriorEvents() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of(EventType.DELIVERED_TO_USER));
        when(dao.findByIdConsistentRead(request.getRequestId())).thenReturn(Optional.empty());
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getEventList()).hasSize(1);
        assertThat(entityCaptor.getValue().getEventList().get(0).getStatus()).isEqualTo(EventType.DELIVERED_TO_USER.name());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void readFlapAfterRead_noRegressionNoRepublish() {
        OutcomePollingRequest request = buildRequest(EventType.READ, 0, true);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        // flap: il poll vede solo la consegna (PROCESSED) ma read_status non è più READ
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of(EventType.DELIVERED_TO_USER));
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.SENT_TO_IO, EventType.DELIVERED_TO_USER, EventType.READ)));
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        verify(dao, never()).update(any());
        verify(eventBridgeProducer, never()).publish(any());

        ArgumentCaptor<SendMessageRequest> sqsCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sqsCaptor.capture());
        OutcomePollingRequest requeued = readRequeued(sqsCaptor.getValue());
        assertThat(requeued.getLastKnownStatus()).isEqualTo(EventType.READ);
        verify(acknowledgement).acknowledge();
    }

    @Test
    void paidBeforeRead_thenRead_reachesFinalState() {
        // Poll 1: vista DELIVERED + PAID (utente ha pagato fuori da IO, non ha ancora letto) -> non finale
        OutcomePollingRequest poll1 = buildRequest(EventType.SENT_TO_IO, 0, true);
        Message<OutcomePollingRequest> message1 = buildMessage(poll1, 0);

        when(ioService.getServiceUseKey(any())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any()))
                .thenReturn(Set.of(EventType.DELIVERED_TO_USER, EventType.PAID));
        when(dao.findByIdConsistentRead(poll1.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.ACCEPTED, EventType.SENT_TO_IO)));
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message1, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(statusNames(entityCaptor.getValue())).contains(EventType.PAID.name());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.PAID.name());
        verify(eventBridgeProducer, times(2)).publish(any()); // DELIVERED_TO_USER + PAID
        verify(sqsClient).sendMessage(any(SendMessageRequest.class)); // non finale -> riaccodato
        verify(acknowledgement).acknowledge();
    }

    @Test
    void paidThenRead_secondPoll_isFinalAndPublishesRead() {
        // Poll 2: ora l'utente legge -> READ è nuovo e chiude il polling (READ && PAID)
        // elapsedSeconds=3700 > pollingIntervalSeconds=3600: bypassa il visibility check (attemptCount>0)
        OutcomePollingRequest poll2 = buildRequest(EventType.PAID, 1, true);
        Message<OutcomePollingRequest> message2 = buildMessage(poll2, 3700);

        when(ioService.getServiceUseKey(any())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any()))
                .thenReturn(Set.of(EventType.DELIVERED_TO_USER, EventType.READ, EventType.PAID));
        when(dao.findByIdConsistentRead(poll2.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.SENT_TO_IO, EventType.DELIVERED_TO_USER, EventType.PAID)));

        pollingWorker.process(message2, acknowledgement);

        ArgumentCaptor<OutcomeEvent> eventCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgeProducer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.READ);
        verify(dao).update(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class)); // finale
        verify(acknowledgement).acknowledge();
    }

    @Test
    void singlePoll_multiState_recordsAllInCanonicalOrder() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, true);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any()))
                .thenReturn(Set.of(EventType.DELIVERED_TO_USER, EventType.READ, EventType.PAID));
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.ACCEPTED, EventType.SENT_TO_IO)));

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        List<String> statuses = statusNames(entityCaptor.getValue());
        assertThat(statuses).containsExactly(
                EventType.ACCEPTED.name(), EventType.SENT_TO_IO.name(),
                EventType.DELIVERED_TO_USER.name(), EventType.READ.name(), EventType.PAID.name());
        verify(eventBridgeProducer, times(3)).publish(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class)); // finale (READ && PAID)
        verify(acknowledgement).acknowledge();
    }

    @Test
    void duplicateRedelivery_idempotent() {
        OutcomePollingRequest request = buildRequest(EventType.READ, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any()))
                .thenReturn(Set.of(EventType.DELIVERED_TO_USER, EventType.READ));
        // tutto già registrato -> nessun evento nuovo. Polling già finale (READ), ma per sicurezza non ripubblica/aggiorna
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.SENT_TO_IO, EventType.DELIVERED_TO_USER, EventType.READ)));
        mockQueueUrl();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder().build());

        pollingWorker.process(message, acknowledgement);

        verify(eventBridgeProducer, never()).publish(any());
        verify(dao, never()).update(any());
        verify(acknowledgement).acknowledge();
    }

    @Test
    void ioThrows_doesNotAck() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenThrow(new RuntimeException("IO 500"));

        assertThatThrownBy(() -> pollingWorker.process(message, acknowledgement))
                .isInstanceOf(RuntimeException.class);

        verify(dao, never()).update(any());
        verify(eventBridgeProducer, never()).publish(any());
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
        verify(acknowledgement, never()).acknowledge();
    }

    @Test
    void failed_terminal_stopsPollingWithoutNotify() {
        OutcomePollingRequest request = buildRequest(EventType.SENT_TO_IO, 0, false);
        Message<OutcomePollingRequest> message = buildMessage(request, 0);

        when(ioService.getServiceUseKey(request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.getReachedEventTypes(any(), any(), any())).thenReturn(Set.of(EventType.IO_DELIVERY_FAILED));
        when(dao.findByIdConsistentRead(request.getRequestId()))
                .thenReturn(Optional.of(existingEntity(EventType.ACCEPTED, EventType.SENT_TO_IO)));

        pollingWorker.process(message, acknowledgement);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.IO_DELIVERY_FAILED.name());
        verify(eventBridgeProducer, never()).publish(any()); // notify=false
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class)); // terminale
        verify(acknowledgement).acknowledge();
    }

    private void mockQueueUrl() {
        when(config.getSqsPollingQueueName()).thenReturn("pn-io-connector-polling-queue");
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("https://sqs/polling-queue").build());
    }

    private OutcomePollingRequest readRequeued(SendMessageRequest sent) {
        try {
            return objectMapper.readValue(sent.messageBody(), OutcomePollingRequest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private IOConnectorRequestEntity existingEntity(EventType... statuses) {
        List<IOConnectorRequestEntity.Event> events = Arrays.stream(statuses)
                .map(s -> IOConnectorRequestEntity.Event.builder()
                        .eventDate("2024-01-01T00:00:00Z")
                        .status(s.name())
                        .build())
                .collect(Collectors.toList());
        return IOConnectorRequestEntity.builder()
                .requestId("REQ-POLL-001")
                .eventList(events)
                .build();
    }

    private List<String> statusNames(IOConnectorRequestEntity entity) {
        return entity.getEventList().stream()
                .map(IOConnectorRequestEntity.Event::getStatus)
                .collect(Collectors.toList());
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
        OutcomePollingRequest requestWithTimestamp = request.toBuilder()
                .enqueuedAt(Instant.now().minusSeconds(elapsedSeconds).toEpochMilli())
                .build();
        return MessageBuilder.withPayload(requestWithTimestamp)
                .setHeader("Sqs_ReceiptHandle", "test-receipt-handle")
                .build();
    }
}
