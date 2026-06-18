package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.MessageContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.PaymentData;
import it.pagopa.pn.ioconnector.middleware.msclient.IOClient;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.model.io.MessageStatusValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IOServiceTest {

    @Mock private IOClient ioClient;
    @Mock private it.pagopa.pn.ioconnector.config.PnIoConnectorConfig pnIoConnectorConfig;
    @InjectMocks private IOService ioService;

    private static final String API_KEY = "test-api-key";

    @Test
    void sendMessage_mapsRequestAndReturnsId() {
        var paymentData = MessageSendRequest.PaymentData.builder()
                .amount(100)
                .noticeCode("301011100007347557")
                .creditorTaxId("01234567890")
                .invalidAfterDueDate(true)
                .build();
        var request = MessageSendRequest.builder()
                .recipientTaxId("RSSMRA80A01H501U")
                .subject("Avviso di pagamento")
                .markdown("Testo del messaggio")
                .dueDate("2026-06-30T23:59:59Z")
                .paymentData(paymentData)
                .build();

        when(ioClient.sendMessage(any(NewMessage.class), eq(API_KEY))).thenReturn("IO-MSG-001");

        String result = ioService.sendMessage(request, API_KEY);

        assertThat(result).isEqualTo("IO-MSG-001");

        ArgumentCaptor<NewMessage> captor = ArgumentCaptor.forClass(NewMessage.class);
        verify(ioClient).sendMessage(captor.capture(), eq(API_KEY));

        NewMessage sent = captor.getValue();
        assertThat(sent.getFiscalCode()).isEqualTo("RSSMRA80A01H501U");
        assertThat(sent.getFeatureLevelType()).isEqualTo("ADVANCED");

        MessageContent content = sent.getContent();
        assertThat(content.getSubject()).isEqualTo("Avviso di pagamento");
        assertThat(content.getMarkdown()).isEqualTo("Testo del messaggio");
        assertThat(content.getDueDate()).isEqualTo("2026-06-30T23:59:59Z");

        PaymentData pd = content.getPaymentData();
        assertThat(pd.getAmount()).isEqualTo(100);
        assertThat(pd.getNoticeNumber()).isEqualTo("301011100007347557");
        assertThat(pd.getInvalidAfterDueDate()).isTrue();
        assertThat(pd.getPayee().getFiscalCode()).isEqualTo("01234567890");
    }

    @Test
    void getReachedEventTypes_emptyWhenClientReturnsNull() {
        when(ioClient.getMessageStatus(any(), any(), any())).thenReturn(null);

        Set<EventType> result = ioService.getReachedEventTypes("RSSMRA80A01H501U", "IO-MSG-001", API_KEY);

        assertThat(result).isEmpty();
    }

    @Test
    void getReachedEventTypes_emptyWhenNotYetDelivered() {
        var response = new ExternalMessageResponseWithContent();
        response.setStatus(MessageStatusValue.THROTTLED);
        when(ioClient.getMessageStatus("RSSMRA80A01H501U", "IO-MSG-001", API_KEY)).thenReturn(response);

        Set<EventType> result = ioService.getReachedEventTypes("RSSMRA80A01H501U", "IO-MSG-001", API_KEY);

        assertThat(result).isEmpty();
    }

    @Test
    void getReachedEventTypes_deliveredWhenProcessed() {
        var response = new ExternalMessageResponseWithContent();
        response.setStatus(MessageStatusValue.PROCESSED);
        when(ioClient.getMessageStatus(any(), any(), any())).thenReturn(response);

        Set<EventType> result = ioService.getReachedEventTypes("RSSMRA80A01H501U", "IO-MSG-001", API_KEY);

        assertThat(result).containsExactly(EventType.DELIVERED_TO_USER);
    }

    @Test
    void getReachedEventTypes_readImpliesDelivered() {
        var response = new ExternalMessageResponseWithContent();
        response.setStatus(MessageStatusValue.PROCESSED);
        response.setReadStatus("READ");
        when(ioClient.getMessageStatus(any(), any(), any())).thenReturn(response);

        Set<EventType> result = ioService.getReachedEventTypes("RSSMRA80A01H501U", "IO-MSG-001", API_KEY);

        assertThat(result).containsExactlyInAnyOrder(EventType.DELIVERED_TO_USER, EventType.READ);
    }

    @Test
    void getReachedEventTypes_paidWithoutRead_doesNotIncludeRead() {
        var response = new ExternalMessageResponseWithContent();
        response.setStatus(MessageStatusValue.PROCESSED);
        response.setReadStatus("UNREAD");
        response.setPaymentStatus("PAID");
        when(ioClient.getMessageStatus(any(), any(), any())).thenReturn(response);

        Set<EventType> result = ioService.getReachedEventTypes("RSSMRA80A01H501U", "IO-MSG-001", API_KEY);

        assertThat(result).containsExactlyInAnyOrder(EventType.DELIVERED_TO_USER, EventType.PAID);
        assertThat(result).doesNotContain(EventType.READ);
    }

    @Test
    void getReachedEventTypes_allThreeWhenProcessedReadPaid() {
        var response = new ExternalMessageResponseWithContent();
        response.setStatus(MessageStatusValue.PROCESSED);
        response.setReadStatus("READ");
        response.setPaymentStatus("PAID");
        when(ioClient.getMessageStatus(any(), any(), any())).thenReturn(response);

        Set<EventType> result = ioService.getReachedEventTypes("RSSMRA80A01H501U", "IO-MSG-001", API_KEY);

        assertThat(result).containsExactlyInAnyOrder(
                EventType.DELIVERED_TO_USER, EventType.READ, EventType.PAID);
    }

    @Test
    void getReachedEventTypes_failedMapsToDeliveryFailed() {
        var response = new ExternalMessageResponseWithContent();
        response.setStatus(MessageStatusValue.FAILED);
        when(ioClient.getMessageStatus(any(), any(), any())).thenReturn(response);

        Set<EventType> result = ioService.getReachedEventTypes("RSSMRA80A01H501U", "IO-MSG-001", API_KEY);

        assertThat(result).containsExactly(EventType.IO_DELIVERY_FAILED);
    }

    @Test
    void getReachedEventTypes_rejectedMapsToDeliveryFailed() {
        var response = new ExternalMessageResponseWithContent();
        response.setStatus(MessageStatusValue.REJECTED);
        when(ioClient.getMessageStatus(any(), any(), any())).thenReturn(response);

        Set<EventType> result = ioService.getReachedEventTypes("RSSMRA80A01H501U", "IO-MSG-001", API_KEY);

        assertThat(result).containsExactly(EventType.IO_DELIVERY_FAILED);
    }

    @Test
    void sendMessage_withNullPaymentData_omitsPaymentData() {
        var request = MessageSendRequest.builder()
                .recipientTaxId("RSSMRA80A01H501U")
                .subject("Avviso senza pagamento")
                .markdown("Testo")
                .paymentData(null)
                .build();

        when(ioClient.sendMessage(any(NewMessage.class), eq(API_KEY))).thenReturn("IO-MSG-002");

        ioService.sendMessage(request, API_KEY);

        ArgumentCaptor<NewMessage> captor = ArgumentCaptor.forClass(NewMessage.class);
        verify(ioClient).sendMessage(captor.capture(), eq(API_KEY));

        assertThat(captor.getValue().getContent().getPaymentData()).isNull();
        assertThat(captor.getValue().getContent().getDueDate()).isNull();
    }

    @Test
    void sendMessage_withValidPdfAttachments_setsThirdPartyData() {
        var attachment = MessageSendRequest.Attachment.builder()
                .id("attach-1")
                .fileKey("documento.pdf")
                .build();
        var request = MessageSendRequest.builder()
                .requestId("REQ-ATTACH-001")
                .recipientTaxId("RSSMRA80A01H501U")
                .subject("Con allegati")
                .markdown("Testo")
                .attachments(List.of(attachment))
                .build();

        when(ioClient.sendMessage(any(NewMessage.class), eq(API_KEY))).thenReturn("IO-MSG-003");

        ioService.sendMessage(request, API_KEY);

        ArgumentCaptor<NewMessage> captor = ArgumentCaptor.forClass(NewMessage.class);
        verify(ioClient).sendMessage(captor.capture(), eq(API_KEY));

        var thirdPartyData = captor.getValue().getContent().getThirdPartyData();
        assertThat(thirdPartyData).isNotNull();
        assertThat(thirdPartyData.getId()).isEqualTo("REQ-ATTACH-001");
        assertThat(thirdPartyData.getHasAttachments()).isTrue();
    }
}
