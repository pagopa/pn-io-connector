package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileResponse;
import it.pagopa.pn.ioconnector.service.DataVaultService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static it.pagopa.pn.ioconnector.utils.LogUtils.GET_IO_PROFILE;

@Service
@CustomLog
@RequiredArgsConstructor
public class ProfileService {

    private final IOService ioService;
    private final DataVaultService dataVaultService;

    public GetProfileResponse getProfile(GetProfileRequest request) {
        log.logStartingProcess(GET_IO_PROFILE);
        try {
            boolean allowed = resolveProfile(
                request.getSenderTaxId(), request.getSenderServiceId(), request.getRecipientTaxId()
            );
            GetProfileResponse.StatusEnum status = allowed
                ? GetProfileResponse.StatusEnum.SENDER_ALLOWED
                : GetProfileResponse.StatusEnum.SENDER_NOT_ALLOWED;
            log.logEndingProcess(GET_IO_PROFILE);
            return new GetProfileResponse().status(status);
        } catch (Exception e) {
            log.logEndingProcess(GET_IO_PROFILE, false, e.getMessage(), e);
            throw e;
        }
    }

    public boolean resolveProfile(String senderTaxId, String senderServiceId, String recipientTaxId) {
        String apiKeyUse = ioService.getServiceUseKey(senderTaxId, senderServiceId);
        String taxId = dataVaultService.deanonymize(recipientTaxId);
        return ioService.checkUserProfile(taxId, apiKeyUse);
    }
}
