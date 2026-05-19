package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @InjectMocks private MessageService messageService;

    @Test
    void handleSendRequest_accepted() {
        MessageResponse result = messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(result.getStatus()).isEqualTo(MessageResponse.StatusEnum.ACCEPTED);
        assertThat(result.getRequestId()).isEqualTo("REQ-001");
        assertThat(result.getxPagopaIoConCxId()).isEqualTo("pn-delivery-push");
    }

    private MessageRequest buildRequest() {
        return new MessageRequest()
            .requestId("REQ-001")
            .iun("IUN-001")
            .recipientTaxId("ANON-TAX")
            .senderServiceId("SVC-001")
            .subject("Test")
            .markdown("body");
    }
}
