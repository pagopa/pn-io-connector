package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.ApiClient;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.DefaultApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.ManageAuthorizationApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.middleware.msclient.common.BaseRestClient;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CustomLog
@Component
@RequiredArgsConstructor
public class IOClient extends BaseRestClient {

    private final PnIoConnectorConfig config;
    private final ManageAuthorizationApi manageAuthorizationApi;
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
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                throw new PnInternalException("IO recipient not found",
                        PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RECIPIENT_NOT_FOUND, ex);
            } else if (status == 429) {
                throw new PnInternalException("IO rate limit exceeded",
                        PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RATE_LIMIT, ex);
            } else if (status >= 500) {
                throw new PnInternalException("IO server error",
                        PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR, ex);
            } else {
                throw new PnInternalException("IO unexpected client error (status=" + status + ")",
                        PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR, ex);
            }
        }
    }

    public ExternalMessageResponseWithContent getMessageStatus(String fiscalCode, String ioMessageId, String apiKeyUse) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.getMessage");
        //TODO: utilizzare defaultIoApi
        return null;
    }

    public String getServiceUseKey(String serviceId, String apiKeyManage) {
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
