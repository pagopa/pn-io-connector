package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
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
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.getProfileByPOST");
        return defaultIoApi(apiKeyUse).getProfileByPOST(fiscalCodePayload);
    }

    public String sendMessage(NewMessage message, String apiKeyUse) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.submitMessageforUserWithFiscalCodeInBody");
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
            log.warn("sendMessage failed — status={} errorCode={}", status, errorCode);
            throw ex;
        }
    }

    public ExternalMessageResponseWithContent getMessageStatus(String fiscalCode, String ioMessageId, String apiKeyUse) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.getMessage");
        //TODO: utilizzare defaultIoApi
        return null;
    }

    public String getServiceUseKey(String serviceId) {
        return apiKeyUseSecrets.get(serviceId);
    }

    private DefaultApi defaultIoApi(String apiKeyUse) {
        return defaultApiCache.computeIfAbsent(apiKeyUse, key -> {
            var apiClient = new ApiClient(
                initRestClient(key).baseUrl(config.getIoBaseUrl()).build()
            );
            return new DefaultApi(apiClient);
        });
    }
}
