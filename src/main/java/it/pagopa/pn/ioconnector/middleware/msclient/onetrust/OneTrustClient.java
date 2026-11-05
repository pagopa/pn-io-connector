package it.pagopa.pn.ioconnector.middleware.msclient.onetrust;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.config.IoLegalSecrets;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Objects;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_MAPPING_ERROR;

@CustomLog
@Component
public class OneTrustClient {

    private static final String PRIVACY_NOTICES_URL = "/api/enterprise-policy/v1/privacynotices/{privacyNoticeId}/published-version";

    private final RestClient oneTrustRestClient;
    private final String oneTrustToken;

    public OneTrustClient(@Qualifier("oneTrustRestClient") RestClient oneTrustRestClient, IoLegalSecrets secrets) {
        this.oneTrustRestClient = oneTrustRestClient;
        this.oneTrustToken = secrets.oneTrustToken();
    }

    /**
     * Restituisce l'ultima versione pubblicata del privacy notice. Il query parameter date e' obbligatorio:
     * passando il giorno successivo OneTrust restituisce sempre la versione piu' recente.
     */
    public PrivacyNoticeOneTrustResult getPrivacyNoticeVersionByPrivacyNoticeId(String privacyNoticeId) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.ONE_TRUST, "getPrivacyNoticeVersionByPrivacyNoticeId");
        PrivacyNoticeOneTrustResponseInt response = oneTrustRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(PRIVACY_NOTICES_URL)
                        .queryParam("date", LocalDate.now().plusDays(1))
                        .build(privacyNoticeId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + oneTrustToken)
                .retrieve()
                .body(PrivacyNoticeOneTrustResponseInt.class);
        return mapToPrivacyNoticeResult(privacyNoticeId, response);
    }

    private PrivacyNoticeOneTrustResult mapToPrivacyNoticeResult(String privacyNoticeId, PrivacyNoticeOneTrustResponseInt response) {
        if (response == null || CollectionUtils.isEmpty(response.versions()) || Objects.isNull(response.versions().get(0))
                || Objects.isNull(response.versions().get(0).majorVersion())) {
            String message = "Error mapping OneTrust privacy notice response: privacyNoticeId=" + privacyNoticeId
                    + " missing published version";
            throw new PnInternalException(message, ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_MAPPING_ERROR);
        }
        return new PrivacyNoticeOneTrustResult(response.versions().get(0).majorVersion());
    }
}
