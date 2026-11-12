package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.ApiClient;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.DefaultApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.middleware.msclient.common.BaseRestClient;
import it.pagopa.pn.ioconnector.utils.LogUtils;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CustomLog
@Component
@RequiredArgsConstructor
public class IOClient extends BaseRestClient {

    private final PnIoConnectorConfig config;
    private final Map<String, String> apiKeyUseSecrets;
    private final Map<String, DefaultApi> defaultApiCache = new ConcurrentHashMap<>();

    public LimitedProfile checkUserProfile(FiscalCodePayload fiscalCodePayload, String apiKeyUse) {
        log.logInvokingExternalDownstreamService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.getProfileByPOST");
        try {
            return defaultIoApi(apiKeyUse).getProfileByPOST(fiscalCodePayload);
        } catch (PnHttpResponseException ex) {
            int status = ex.getStatusCode();
            String errorCode = switch (status) {
                case 404 -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RECIPIENT_NOT_FOUND;
                case 429 -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RATE_LIMIT;
                default -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR;
            };
            String description = String.format("checkUserProfile failed - status=%d errorCode=%s", status, errorCode);
            if (status == 404) {
                log.logInvokationResultDownstreamNotFound(PnLogger.EXTERNAL_SERVICES.IO, description);
            } else {
                log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.IO, description, null);
            }
            throw ex;
        } catch (RestClientException ex) {
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.IO,
                    "checkUserProfile failed - " + ex.getClass().getSimpleName(), null);
            throw ex;
        }
    }

    public String sendMessage(NewMessage message, String apiKeyUse) {
        log.logInvokingExternalDownstreamService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.submitMessageforUserWithFiscalCodeInBody");
        try {
            return defaultIoApi(apiKeyUse)
                    .submitMessageforUserWithFiscalCodeInBody(message)
                    .getId();
        } catch (PnHttpResponseException ex) {
            int status = ex.getStatusCode();
            String errorCode = switch (status) {
                case 404 -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RECIPIENT_NOT_FOUND;
                case 429 -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RATE_LIMIT;
                default -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR;
            };
            String description = String.format("sendMessage failed - status=%d errorCode=%s", status, errorCode);
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.IO, description, null);
            throw ex;
        } catch (RestClientException ex) {
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.IO,
                    "sendMessage failed - " + ex.getClass().getSimpleName(), null);
            throw ex;
        }
    }

    public ExternalMessageResponseWithContent getMessageStatus(String fiscalCode, String ioMessageId, String apiKeyUse) {
        log.logInvokingExternalDownstreamService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.getMessage");
        try {
            return defaultIoApi(apiKeyUse).getMessage(fiscalCode, ioMessageId);
        } catch (PnHttpResponseException ex) {
            int status = ex.getStatusCode();
            String errorCode = switch (status) {
                case 404 -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_MESSAGE_NOT_FOUND;
                case 429 -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RATE_LIMIT;
                default -> PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR;
            };
            String description = String.format("getMessageStatus failed - status=%d errorCode=%s", status, errorCode);
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.IO, description, null);
            throw new PnHttpResponseException(LogUtils.maskTaxIdInPath(ex.getMessage()), ex.getStatusCode());
        } catch (RestClientException ex) {
            log.logInvokationResultDownstreamFailed(PnLogger.EXTERNAL_SERVICES.IO,
                    "getMessageStatus failed - " + ex.getClass().getSimpleName(), null);
            throw new PnInternalException(LogUtils.maskTaxIdInPath(ex.getMessage()), PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR);
        }
    }

    public String getServiceUseKey(String serviceId) {
        return apiKeyUseSecrets.get(serviceId);
    }

    private DefaultApi defaultIoApi(String apiKeyUse) {
        return defaultApiCache.computeIfAbsent(apiKeyUse, key -> {
            var apiClient = new ApiClient(initRestClient(key).build());
            apiClient.setBasePath(config.getIoBaseUrl());
            return new DefaultApi(apiClient);
        });
    }
}
