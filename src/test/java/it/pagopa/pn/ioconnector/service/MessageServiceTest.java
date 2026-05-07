package it.pagopa.pn.ioconnector.service;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.service.io.IOService;
import it.pagopa.pn.ioconnector.service.io.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private DataVaultService dataVaultService;
    @Mock private IOService ioService;

    @InjectMocks private MessageService messageService;

    @Test
    void handleSendRequest_accepted() {
        when(ioService.getServiceUseKey("SENDER-TAX", "SVC-001")).thenReturn("key");
        when(dataVaultService.deanonymize("ANON-TAX")).thenReturn("CF");
        when(ioService.checkUserProfile("CF", "key")).thenReturn(true);

        MessageResponse result = messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(result.getStatus()).isEqualTo(MessageResponse.StatusEnum.ACCEPTED);
        assertThat(result.getRequestId()).isEqualTo("REQ-001");
        assertThat(result.getCxId()).isEqualTo("pn-delivery-push");
    }

    @Test
    void handleSendRequest_notAccepted() {
        when(ioService.getServiceUseKey("SENDER-TAX", "SVC-001")).thenReturn("key");
        when(dataVaultService.deanonymize("ANON-TAX")).thenReturn("CF");
        when(ioService.checkUserProfile("CF", "key")).thenReturn(false);

        MessageResponse result = messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(result.getStatus()).isEqualTo(MessageResponse.StatusEnum.NOT_ACCEPTED);
        assertThat(result.getRequestId()).isEqualTo("REQ-001");
        assertThat(result.getCxId()).isEqualTo("pn-delivery-push");
    }

    private MessageRequest buildRequest() {
        return new MessageRequest()
            .requestId("REQ-001")
            .iun("IUN-001")
            .recipientTaxId("ANON-TAX")
            .senderTaxId("SENDER-TAX")
            .senderServiceId("SVC-001")
            .subject("Test")
            .markdown("body");
    }
}
