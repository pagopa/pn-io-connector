package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.config.IoWhitelistChecker;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.DefaultApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.middleware.msclient.common.BaseRestClient;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@CustomLog
@Component
public class IOLegalClient extends BaseRestClient {

    private static final String WHITELIST_BLOCK_LOG = "IO whitelist - recipient not allowed, skipping real call to IO for operation {}";

    private final DefaultApi ioLegalApi;
    private final DefaultApi ioOptInApi;
    private final IoWhitelistChecker whitelistChecker;

    public IOLegalClient(@Qualifier("ioLegalApi") DefaultApi ioLegalApi,
                         @Qualifier("ioOptInApi") DefaultApi ioOptInApi,
                         IoWhitelistChecker whitelistChecker) {
        this.ioLegalApi = ioLegalApi;
        this.ioOptInApi = ioOptInApi;
        this.whitelistChecker = whitelistChecker;
    }

    public LimitedProfile getProfile(FiscalCodePayload payload) {
        if (!whitelistChecker.isAllowed(payload.getFiscalCode())) {
            log.warn(WHITELIST_BLOCK_LOG, "getProfileByPOST");
            throw new PnHttpResponseException("Recipient not in IO whitelist", 404);
        }
        log.logInvokingExternalDownstreamService(PnLogger.EXTERNAL_SERVICES.IO, "ioLegalApi.getProfileByPOST");
        return invoke("getProfileByPOST", true, () -> ioLegalApi.getProfileByPOST(payload));
    }

    public Activation getActivation(FiscalCodePayload payload) {
        if (!whitelistChecker.isAllowed(payload.getFiscalCode())) {
            log.warn(WHITELIST_BLOCK_LOG, "getServiceActivationByPOST");
            return simulatedInactiveActivation(payload.getFiscalCode());
        }
        log.logInvokingExternalDownstreamService(PnLogger.EXTERNAL_SERVICES.IO, "ioLegalApi.getServiceActivationByPOST");
        return invoke("getServiceActivationByPOST", true, () -> ioLegalApi.getServiceActivationByPOST(payload));
    }

    public Activation upsertActivation(ActivationPayload payload) {
        if (!whitelistChecker.isAllowed(payload.getFiscalCode())) {
            log.warn(WHITELIST_BLOCK_LOG, "upsertServiceActivation");
            return simulatedInactiveActivation(payload.getFiscalCode());
        }
        log.logInvokingExternalDownstreamService(PnLogger.EXTERNAL_SERVICES.IO, "ioLegalApi.upsertServiceActivation");
        return invoke("upsertServiceActivation", false, () -> ioLegalApi.upsertServiceActivation(payload));
    }

    public String sendCourtesyMessage(NewMessage message) {
        return sendMessage(ioLegalApi, message, "ioLegalApi.submitMessageforUserWithFiscalCodeInBody");
    }

    public String sendOptInMessage(NewMessage message) {
        return sendMessage(ioOptInApi, message, "ioOptInApi.submitMessageforUserWithFiscalCodeInBody");
    }

    private String sendMessage(DefaultApi api, NewMessage message, String operation) {
        if (!whitelistChecker.isAllowed(message.getFiscalCode())) {
            log.warn(WHITELIST_BLOCK_LOG, operation);
            return UUID.randomUUID().toString();
        }
        log.logInvokingExternalDownstreamService(PnLogger.EXTERNAL_SERVICES.IO, operation);
        return invoke(operation, false, () -> api.submitMessageforUserWithFiscalCodeInBody(message).getId());
    }

    private Activation simulatedInactiveActivation(String fiscalCode) {
        return new Activation()
                .fiscalCode(fiscalCode)
                .status("INACTIVE");
    }

    /**
     * Mappa gli errori HTTP di IO sui codici di errore del pn-io-connector
     */
    private <T> T invoke(String operation, boolean notFoundIsExpected, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (PnHttpResponseException ex) {
            int status = ex.getStatusCode();
            String errorCode = switch (status) {
                case 404 -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RECIPIENT_NOT_FOUND;
                case 429 -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RATE_LIMIT;
                default -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR;
            };
            String description = String.format("%s failed - status=%d errorCode=%s", operation, status, errorCode);
            if (status == 404 && notFoundIsExpected) {
                log.logInvokationResultDownstreamNotFound(PnLogger.EXTERNAL_SERVICES.IO, description);
            } else {
                log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.IO, description, null);
            }
            throw ex;
        } catch (RestClientException ex) {
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.IO,
                    operation + " failed - " + ex.getClass().getSimpleName(), null);
            throw ex;
        }
    }
}
