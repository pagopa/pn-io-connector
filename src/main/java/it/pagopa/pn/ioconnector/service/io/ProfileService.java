package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
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
            String apiKeyUse = ioService.getServiceUseKey(request.getSenderServiceId());
            String taxId = dataVaultService.deanonymize(request.getRecipientTaxId());
            LimitedProfile lp = ioService.checkUserProfile(taxId, apiKeyUse);
            GetProfileResponse getProfileResponse = new GetProfileResponse();
            getProfileResponse.setStatus(
                    Boolean.TRUE.equals(lp.getSenderAllowed()) ?
                            GetProfileResponse.StatusEnum.SENDER_ALLOWED :
                            GetProfileResponse.StatusEnum.SENDER_NOT_ALLOWED
            );
            getProfileResponse.setPreferredLanguages(Boolean.TRUE.equals(lp.getSenderAllowed()) ? lp.getPreferredLanguages() : null);
            log.logEndingProcess(GET_IO_PROFILE);
            return getProfileResponse;
        } catch (Exception e) {
            log.logEndingProcess(GET_IO_PROFILE, false, e.getMessage(), e);
            throw e;
        }
    }

    public boolean resolveProfile(String serviceId, String recipientTaxId) {
        String apiKeyUse = ioService.getServiceUseKey(serviceId);
        String taxId = dataVaultService.deanonymize(recipientTaxId);
        LimitedProfile lp =  ioService.checkUserProfile(taxId, apiKeyUse);
        return lp.getSenderAllowed();
    }
}
