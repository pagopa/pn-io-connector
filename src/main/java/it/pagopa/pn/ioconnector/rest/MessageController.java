package it.pagopa.pn.ioconnector.rest;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.api.MessageApi;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.service.io.MessageService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RestController
@RequiredArgsConstructor
public class MessageController implements MessageApi {

    private final MessageService messageService;

    @Override
    public ResponseEntity<MessageResponse> sendIOMessage(String xPagopaIoConCxId, MessageRequest messageRequest) {
        MessageResponse response = messageService.handleSendRequest(xPagopaIoConCxId, messageRequest);
        return MessageResponse.StatusEnum.ACCEPTED.equals(response.getStatus())
            ? ResponseEntity.accepted().body(response)
            : ResponseEntity.ok(response);
    }
}
