package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.externalregistries.v1.api.PrivacyNoticeApi;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@CustomLog
@Component
@RequiredArgsConstructor
public class ExternalRegistriesPrivacyNoticeClient {

    private final PrivacyNoticeApi privacyNoticeApi;

    public Integer findPrivacyNoticeVersion(String consentsType, String portalType) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.PN_EXTERNAL_REGISTRIES, "privacyNoticeApi.findPrivacyNoticeVersion");
        try {
            return privacyNoticeApi.findPrivacyNoticeVersion(consentsType, portalType).getVersion();
        } catch (PnHttpResponseException | RestClientException e) {
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.PN_EXTERNAL_REGISTRIES,
                    "findPrivacyNoticeVersion failed: consentsType=" + consentsType + " portalType=" + portalType, e);
            throw e;
        }
    }
}
