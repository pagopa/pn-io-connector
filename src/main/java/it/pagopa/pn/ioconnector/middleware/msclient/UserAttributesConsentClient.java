package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.api.ConsentsApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.ConsentAction;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.ConsentType;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.CxTypeAuthFleet;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@CustomLog
@Component
@RequiredArgsConstructor
public class UserAttributesConsentClient {

    private final ConsentsApi consentsApi;

    public void acceptConsent(String uid, String cxId, CxTypeAuthFleet cxType, ConsentType consentType, Integer version) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.PN_USER_ATTRIBUTES, "consentsApi.consentAction");
        ConsentAction consentAction = new ConsentAction()
                .action(ConsentAction.ActionEnum.ACCEPT)
                .channel(ConsentAction.ChannelEnum.IO);
        try {
            consentsApi.consentAction(uid, cxId, cxType, consentType, String.valueOf(version), consentAction);
        } catch (PnHttpResponseException | RestClientException e) {
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.PN_USER_ATTRIBUTES,
                    "consentAction failed: consentType=" + consentType.getValue() + " version=" + version, e);
            throw e;
        }
    }
}
