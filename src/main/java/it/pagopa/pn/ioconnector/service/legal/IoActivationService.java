package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.ConsentType;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationStatus;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.middleware.msclient.IOLegalClient;
import it.pagopa.pn.ioconnector.middleware.msclient.UserAttributesConsentClient;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import static it.pagopa.pn.ioconnector.service.legal.IoActivationMapper.toActivation;
import static it.pagopa.pn.ioconnector.service.legal.IoActivationMapper.toIoActivationPayload;
import static it.pagopa.pn.ioconnector.service.legal.IoActivationMapper.toIoFiscalCodePayload;
import static it.pagopa.pn.ioconnector.service.legal.IoActivationMapper.toUserAttributesCxType;

@CustomLog
@Service
@RequiredArgsConstructor
public class IoActivationService {

    private static final String PRIVACY_NOTICE_PORTAL_TYPE_PF = "PF";

    private final IOLegalClient ioLegalClient;
    private final PrivacyNoticeService privacyNoticeService;
    private final UserAttributesConsentClient userAttributesConsentClient;

    public Activation getActivation(FiscalCodePayload request) {
        return toActivation(ioLegalClient.getActivation(toIoFiscalCodePayload(request.getFiscalCode())));
    }

    public Activation upsertActivation(String xPagopaPnUid,
                                       CxTypeAuthFleet xPagopaPnCxType,
                                       String xPagopaPnCxId,
                                       ActivationPayload request) {
        log.info("Upsert IO activation requested with status={}", request.getStatus());
        if (ActivationStatus.ACTIVE.equals(request.getStatus())) {
            acceptConsent(xPagopaPnUid, xPagopaPnCxId, xPagopaPnCxType, ConsentType.TOS);
            acceptConsent(xPagopaPnUid, xPagopaPnCxId, xPagopaPnCxType, ConsentType.DATAPRIVACY);
        }
        return upsertOnIoOrReadCurrent(request);
    }

    private Activation upsertOnIoOrReadCurrent(ActivationPayload request) {
        try {
            return toActivation(ioLegalClient.upsertActivation(toIoActivationPayload(request)));
        } catch (PnHttpResponseException | RestClientException e) {
            log.warn("Upsert IO activation failed, reading current activation from IO as fallback: {}", e.toString());
            return toActivation(ioLegalClient.getActivation(toIoFiscalCodePayload(request.getFiscalCode())));
        }
    }

    private void acceptConsent(String uid, String cxId, CxTypeAuthFleet cxType, ConsentType consentType) {
        Integer version = privacyNoticeService.findPrivacyNoticeVersion(consentType.getValue(), PRIVACY_NOTICE_PORTAL_TYPE_PF);
        userAttributesConsentClient.acceptConsent(uid, cxId, toUserAttributesCxType(cxType), consentType, version);
        log.info("Consent accepted: consentType={} version={}", consentType.getValue(), version);
    }
}
