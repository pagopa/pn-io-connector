package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.exceptions.PnNotImplementedException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import it.pagopa.pn.ioconnector.middleware.msclient.IOLegalClient;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@CustomLog
@Service
@RequiredArgsConstructor
public class SendIoMessageService {

    private final IOLegalClient ioLegalClient;

    public UserStatusResponse getUserStatus(UserStatusRequest request) {
        String taxId = request.getTaxId();
        UserStatusResponse.StatusEnum status;
        try {
            LimitedProfile profile = ioLegalClient.getProfile(new FiscalCodePayload().fiscalCode(taxId));
            status = Boolean.TRUE.equals(profile.getSenderAllowed())
                    ? UserStatusResponse.StatusEnum.PN_ACTIVE
                    : UserStatusResponse.StatusEnum.PN_NOT_ACTIVE;
        } catch (PnHttpResponseException e) {
            if (e.getStatusCode() == 404) {
                status = UserStatusResponse.StatusEnum.APPIO_NOT_ACTIVE;
            } else {
                log.warn("getUserStatus failed for taxId={} - status={}", taxId, e.getStatusCode());
                status = UserStatusResponse.StatusEnum.ERROR;
            }
        }
        return new UserStatusResponse().taxId(taxId).status(status);
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        throw new PnNotImplementedException("sendLegalIOMessage");
    }
}
