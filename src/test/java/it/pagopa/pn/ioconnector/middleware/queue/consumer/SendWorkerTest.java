package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.middleware.queue.producer.EventBridgePublisher;
import it.pagopa.pn.ioconnector.middleware.queue.producer.PollingQueuePublisher;
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
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;

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
    @Mock private EventBridgePublisher eventBridgePublisher;
    @Mock private PollingQueuePublisher pollingQueuePublisher;

    @InjectMocks private SendWorker sendWorker;

    @Test
    void senderNotAllowed_whenProfileReturnsFalse() {
        MessageSendRequest request = buildRequest();
        when(ioService.getServiceUseKey(request.getSenderTaxId(), request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(false);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);

        sendWorker.process(request);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENDER_NOT_ALLOWED.name());

        ArgumentCaptor<OutcomeEvent> eventCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgePublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.SENDER_NOT_ALLOWED);

        verify(ioService, never()).sendMessage(any(), any());
        verify(pollingQueuePublisher, never()).publish(any());
    }

    @Test
    void senderNotAllowed_when404OnProfile() {
        MessageSendRequest request = buildRequest();
        when(ioService.getServiceUseKey(request.getSenderTaxId(), request.getSenderServiceId())).thenReturn("api-key");
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key"))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        sendWorker.process(request);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENDER_NOT_ALLOWED.name());

        ArgumentCaptor<OutcomeEvent> eventCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgePublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.SENDER_NOT_ALLOWED);

        verify(ioService, never()).sendMessage(any(), any());
        verify(pollingQueuePublisher, never()).publish(any());
    }

    @Test
    void sendSuccess_updatesDbAndPublishesEvents() {
        MessageSendRequest request = buildRequest();
        when(ioService.getServiceUseKey(request.getSenderTaxId(), request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenReturn("IO-MSG-001");

        sendWorker.process(request);

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(dao).update(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(EventType.SENT_TO_IO.name());
        assertThat(entityCaptor.getValue().getIoMessageId()).isEqualTo("IO-MSG-001");

        ArgumentCaptor<OutcomeEvent> eventCaptor = ArgumentCaptor.forClass(OutcomeEvent.class);
        verify(eventBridgePublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.SENT_TO_IO);

        verify(pollingQueuePublisher).publish(any(OutcomePollingRequest.class));
    }

    @Test
    void propagatesException_onSendMessageError() {
        MessageSendRequest request = buildRequest();
        when(ioService.getServiceUseKey(request.getSenderTaxId(), request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key")))
                .thenThrow(new RuntimeException("IO 500"));

        assertThatThrownBy(() -> sendWorker.process(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("IO 500");

        verify(dao, never()).update(any());
        verify(eventBridgePublisher, never()).publish(any());
    }

    @Test
    void buildsPollingRequest_withPaymentData() {
        MessageSendRequest request = buildRequest();
        request.setPaymentData(MessageSendRequest.PaymentData.builder()
                .amount(100)
                .noticeCode("302000000000000000")
                .creditorTaxId("77777777777")
                .build());

        when(ioService.getServiceUseKey(request.getSenderTaxId(), request.getSenderServiceId())).thenReturn("api-key");
        LimitedProfile profile = new LimitedProfile();
        profile.setSenderAllowed(true);
        when(ioService.checkUserProfile(request.getRecipientTaxId(), "api-key")).thenReturn(profile);
        when(ioService.sendMessage(eq(request), eq("api-key"))).thenReturn("IO-MSG-002");

        sendWorker.process(request);

        ArgumentCaptor<OutcomePollingRequest> pollingCaptor = ArgumentCaptor.forClass(OutcomePollingRequest.class);
        verify(pollingQueuePublisher).publish(pollingCaptor.capture());
        assertThat(pollingCaptor.getValue().isPaymentData()).isTrue();
    }

    private MessageSendRequest buildRequest() {
        return MessageSendRequest.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("RSSMRA80A01H501U")
                .senderTaxId("12345678901")
                .senderServiceId("SVC-001")
                .subject("Test subject")
                .markdown("Test body")
                .pollingMaxDate(Instant.now().plusSeconds(3600))
                .build();
    }
}
