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
import static it.pagopa.pn.ioconnector.utils.LogUtils.GET_IO_ACTIVATION;
import static it.pagopa.pn.ioconnector.utils.LogUtils.UPSERT_IO_ACTIVATION;

@CustomLog
@Service
@RequiredArgsConstructor
public class IoActivationService {

    private static final String PRIVACY_NOTICE_PORTAL_TYPE_PF = "PF";

    private final IOLegalClient ioLegalClient;
    private final PrivacyNoticeService privacyNoticeService;
    private final UserAttributesConsentClient userAttributesConsentClient;

    public Activation getActivation(FiscalCodePayload request) {
        log.logStartingProcess(GET_IO_ACTIVATION);
        try {
            Activation activation = toActivation(ioLegalClient.getActivation(toIoFiscalCodePayload(request.getFiscalCode())));
            log.logEndingProcess(GET_IO_ACTIVATION);
            return activation;
        } catch (RuntimeException e) {
            log.logEndingProcess(GET_IO_ACTIVATION, false, e.getMessage(), e);
            throw e;
        }
    }

    public Activation upsertActivation(String xPagopaPnUid,
                                       CxTypeAuthFleet xPagopaPnCxType,
                                       String xPagopaPnCxId,
                                       ActivationPayload request) {
        log.logStartingProcess(UPSERT_IO_ACTIVATION);
        try {
            log.info("Upsert IO activation requested with status={}", request.getStatus());
            if (ActivationStatus.ACTIVE.equals(request.getStatus())) {
                acceptConsent(xPagopaPnUid, xPagopaPnCxId, xPagopaPnCxType, ConsentType.TOS);
                acceptConsent(xPagopaPnUid, xPagopaPnCxId, xPagopaPnCxType, ConsentType.DATAPRIVACY);
            }
            Activation activation = upsertOnIoOrReadCurrent(request);
            log.logEndingProcess(UPSERT_IO_ACTIVATION);
            return activation;
        } catch (RuntimeException e) {
            log.logEndingProcess(UPSERT_IO_ACTIVATION, false, e.getMessage(), e);
            throw e;
        }
    }

    private Activation upsertOnIoOrReadCurrent(ActivationPayload request) {
        try {
            return toActivation(ioLegalClient.upsertActivation(toIoActivationPayload(request)));
        } catch (PnHttpResponseException | RestClientException e) {
            log.warn("Upsert IO activation failed, reading current activation from IO as fallback: {}", e.getMessage());
            return toActivation(ioLegalClient.getActivation(toIoFiscalCodePayload(request.getFiscalCode())));
        }
    }

    private void acceptConsent(String uid, String cxId, CxTypeAuthFleet cxType, ConsentType consentType) {
        Integer version = privacyNoticeService.findPrivacyNoticeVersion(consentType.getValue(), PRIVACY_NOTICE_PORTAL_TYPE_PF);
        userAttributesConsentClient.acceptConsent(uid, cxId, toUserAttributesCxType(cxType), consentType, version);
        log.info("Consent accepted: consentType={} version={}", consentType.getValue(), version);
    }
}
