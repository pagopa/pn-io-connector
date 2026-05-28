package it.pagopa.pn.ioconnector.rest;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.api.IoConnectorApi;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.service.io.GetMessageService;
import it.pagopa.pn.ioconnector.service.io.MessageService;
import it.pagopa.pn.ioconnector.service.io.ProfileService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

@CustomLog
@RestController
@RequiredArgsConstructor
public class IOController implements IoConnectorApi {

    private final MessageService messageService;
    private final ProfileService profileService;
    private final GetMessageService getMessageService;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<MessageResponse> sendIOMessage(String xPagopaIoConCxId, MessageRequest messageRequest) {
        Optional<MessageResponse> result = messageService.handleSendRequest(xPagopaIoConCxId, messageRequest);
        return result.isPresent()
            ? ResponseEntity.ok(result.get())
            : ResponseEntity.status(HttpStatus.NO_CONTENT).<MessageResponse>build();
    }

    @Override
    public ResponseEntity<GetProfileResponse> getIOProfile(String xPagopaIoConCxId, GetProfileRequest request) {
        return ResponseEntity.ok(profileService.getProfile(request));
    }

    @Override
    public ResponseEntity<GetMessageResponse> getMessage(String id, String xPagopaPnCxId) {
        return ResponseEntity.ok(getMessageService.getMessageDetails(id, xPagopaPnCxId));
    }
}
