package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.config.PrivacyNoticeDTO;
import it.pagopa.pn.ioconnector.config.PrivacyNoticeMapping;
import it.pagopa.pn.ioconnector.exceptions.PnPrivacyNoticeNotFoundException;
import it.pagopa.pn.ioconnector.middleware.msclient.onetrust.OneTrustClient;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_MAPPING_ERROR;

@CustomLog
@Service
@RequiredArgsConstructor
public class PrivacyNoticeService {

    private final PrivacyNoticeMapping privacyNoticeMapping;
    private final OneTrustClient oneTrustClient;
    private final Map<String, Integer> privacyNoticeCache = new ConcurrentHashMap<>();

    public Integer findPrivacyNoticeVersion(String consentsType, String portalType) {
        PrivacyNoticeDTO privacyNotice = privacyNoticeMapping.notices().stream()
                .filter(notice -> Objects.equals(notice.consentsType(), consentsType) && Objects.equals(notice.portalType(), portalType))
                .findFirst()
                .orElseThrow(() -> new PnPrivacyNoticeNotFoundException(consentsType, portalType));
        try {
            Integer version = oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(privacyNotice.privacyNoticeId())
                    .version();
            privacyNoticeCache.put(privacyNotice.privacyNoticeId(), version);
            log.info("Privacy notice version retrieved from OneTrust: consentsType={} portalType={} version={}",
                    consentsType, portalType, version);
            return version;
        } catch (PnHttpResponseException | RestClientException | PnInternalException e) {
            return versionFromCacheOrDefault(privacyNotice, e);
        }
    }

    private Integer versionFromCacheOrDefault(PrivacyNoticeDTO privacyNotice, RuntimeException cause) {
        Integer cachedVersion = privacyNoticeCache.get(privacyNotice.privacyNoticeId());
        if (cachedVersion != null) {
            log.warn("OneTrust call failed, using cached privacy notice version: consentsType={} portalType={} version={} cause={}",
                    privacyNotice.consentsType(), privacyNotice.portalType(), cachedVersion, cause.getMessage());
            return cachedVersion;
        }
        if (privacyNotice.defaultVersion() == null) {
            String message = "OneTrust call failed, no cached version and no default version configured: consentsType="
                    + privacyNotice.consentsType() + " portalType=" + privacyNotice.portalType();
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.ONE_TRUST, message, cause);
            throw new PnInternalException(message, ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_MAPPING_ERROR, cause);
        }
        log.warn("OneTrust call failed and no cached version, using default privacy notice version: consentsType={} portalType={} version={} cause={}",
                privacyNotice.consentsType(), privacyNotice.portalType(), privacyNotice.defaultVersion(), cause.getMessage());
        return privacyNotice.defaultVersion();
    }
}
