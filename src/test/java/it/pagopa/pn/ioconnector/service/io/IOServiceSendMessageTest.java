package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.config.IoServiceConfigurationResolver;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.PaymentData;
import it.pagopa.pn.ioconnector.middleware.msclient.IOClient;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IOServiceSendMessageTest {

    @Mock private IOClient ioClient;
    @Mock private PnIoConnectorConfig pnIoConnectorConfig;
    @Mock private IoServiceConfigurationResolver ioServiceConfigurationResolver;

    @InjectMocks private IOService ioService;

    @Test
    void sendMessage_buildsNewMessageWithAdvancedFeatureLevel() {
        MessageSendRequest request = buildRequest();
        when(ioClient.sendMessage(any(NewMessage.class), eq("api-key"))).thenReturn("IO-MSG-001");

        ioService.sendMessage(request, "api-key");

        ArgumentCaptor<NewMessage> captor = ArgumentCaptor.forClass(NewMessage.class);
        verify(ioClient).sendMessage(captor.capture(), eq("api-key"));
        assertThat(captor.getValue().getFeatureLevelType()).isEqualTo("ADVANCED");
    }

    @Test
    void sendMessage_setsThirdPartyDataWhenAttachmentsPresent() {
        MessageSendRequest request = buildRequest();
        request.setAttachments(List.of(
                MessageSendRequest.Attachment.builder().fileKey("file-key-1.pdf").build())
        );
        when(ioClient.sendMessage(any(NewMessage.class), eq("api-key"))).thenReturn("IO-MSG-001");

        ioService.sendMessage(request, "api-key");

        ArgumentCaptor<NewMessage> captor = ArgumentCaptor.forClass(NewMessage.class);
        verify(ioClient).sendMessage(captor.capture(), eq("api-key"));
        assertThat(captor.getValue().getContent().getThirdPartyData()).isNotNull();
        assertThat(captor.getValue().getContent().getThirdPartyData().getHasAttachments()).isTrue();
    }

    @Test
    void sendMessage_setsPayeeWhenCreditorTaxIdDiffersFromOrganizationFiscalCode() {
        MessageSendRequest request = buildRequestWithPayment("01234567890");
        when(ioServiceConfigurationResolver.getOrganizationFiscalCode("SVC-001")).thenReturn("12345678910");
        when(ioClient.sendMessage(any(NewMessage.class), eq("api-key"))).thenReturn("IO-MSG-001");

        ioService.sendMessage(request, "api-key");

        assertThat(capturePaymentData().getPayee().getFiscalCode()).isEqualTo("01234567890");
    }

    @Test
    void sendMessage_omitsPayeeWhenCreditorTaxIdMatchesOrganizationFiscalCode() {
        MessageSendRequest request = buildRequestWithPayment("12345678910");
        when(ioServiceConfigurationResolver.getOrganizationFiscalCode("SVC-001")).thenReturn("12345678910");
        when(ioClient.sendMessage(any(NewMessage.class), eq("api-key"))).thenReturn("IO-MSG-001");

        ioService.sendMessage(request, "api-key");

        PaymentData paymentData = capturePaymentData();
        assertThat(paymentData.getPayee()).isNull();
        assertThat(paymentData.getAmount()).isEqualTo(100);
        assertThat(paymentData.getNoticeNumber()).isEqualTo("301011100007347557");
        assertThat(paymentData.getInvalidAfterDueDate()).isTrue();
    }

    @Test
    void sendMessage_omitsPayeeWhenCreditorTaxIdMatchesIgnoringCase() {
        MessageSendRequest request = buildRequestWithPayment("rssmra80a01h501u");
        when(ioServiceConfigurationResolver.getOrganizationFiscalCode("SVC-001")).thenReturn("RSSMRA80A01H501U");
        when(ioClient.sendMessage(any(NewMessage.class), eq("api-key"))).thenReturn("IO-MSG-001");

        ioService.sendMessage(request, "api-key");

        assertThat(capturePaymentData().getPayee()).isNull();
    }

    @Test
    void sendMessage_throwsWhenOrganizationFiscalCodeIsMissing() {
        MessageSendRequest request = buildRequestWithPayment("01234567890");
        when(ioServiceConfigurationResolver.getOrganizationFiscalCode("SVC-001")).thenReturn(null);

        assertThatThrownBy(() -> ioService.sendMessage(request, "api-key"))
                .isInstanceOfSatisfying(PnInternalException.class, e -> {
                    assertThat(e.getProblem().getDetail()).contains("SVC-001");
                    assertThat(e.getProblem().getErrors().get(0).getCode())
                            .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_SERVICE_NOT_CONFIGURED);
                });
        verifyNoInteractions(ioClient);
    }

    @Test
    void sendMessage_throwsWhenOrganizationFiscalCodeIsBlank() {
        MessageSendRequest request = buildRequestWithPayment("01234567890");
        when(ioServiceConfigurationResolver.getOrganizationFiscalCode("SVC-001")).thenReturn("   ");

        assertThatThrownBy(() -> ioService.sendMessage(request, "api-key"))
                .isInstanceOfSatisfying(PnInternalException.class, e ->
                        assertThat(e.getProblem().getErrors().get(0).getCode())
                                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_SERVICE_NOT_CONFIGURED));
        verifyNoInteractions(ioClient);
    }

    private PaymentData capturePaymentData() {
        ArgumentCaptor<NewMessage> captor = ArgumentCaptor.forClass(NewMessage.class);
        verify(ioClient).sendMessage(captor.capture(), eq("api-key"));
        return captor.getValue().getContent().getPaymentData();
    }

    private MessageSendRequest buildRequestWithPayment(String creditorTaxId) {
        MessageSendRequest request = buildRequest();
        request.setPaymentData(MessageSendRequest.PaymentData.builder()
                .amount(100)
                .noticeCode("301011100007347557")
                .creditorTaxId(creditorTaxId)
                .invalidAfterDueDate(true)
                .build());
        return request;
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
}
