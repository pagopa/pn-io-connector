package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IOServiceSendMessageTest {

    @Mock private IOClient ioClient;
    @Mock private PnIoConnectorConfig pnIoConnectorConfig;

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
