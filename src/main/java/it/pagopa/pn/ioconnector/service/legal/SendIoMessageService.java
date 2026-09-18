package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.ioconnector.exceptions.PnNotImplementedException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@CustomLog
@Service
@RequiredArgsConstructor
public class SendIoMessageService {

    public UserStatusResponse getUserStatus(UserStatusRequest request) {
        throw new PnNotImplementedException("getIoUserStatus");
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        throw new PnNotImplementedException("sendLegalIOMessage");
    }
}
