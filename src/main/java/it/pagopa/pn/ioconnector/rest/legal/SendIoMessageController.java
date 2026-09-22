package it.pagopa.pn.ioconnector.rest.legal;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.api.SendIoMessageApi;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import it.pagopa.pn.ioconnector.service.legal.SendIoMessageService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

@CustomLog
@RestController
@RequiredArgsConstructor
public class SendIoMessageController implements SendIoMessageApi {

    private final SendIoMessageService sendIoMessageService;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<UserStatusResponse> getIoUserStatus(UserStatusRequest userStatusRequest) {
        return ResponseEntity.ok(sendIoMessageService.getUserStatus(userStatusRequest));
    }

    @Override
    public ResponseEntity<SendMessageResponse> sendLegalIOMessage(SendMessageRequest sendMessageRequest) {
        return ResponseEntity.ok(sendIoMessageService.sendMessage(sendMessageRequest));
    }
}
