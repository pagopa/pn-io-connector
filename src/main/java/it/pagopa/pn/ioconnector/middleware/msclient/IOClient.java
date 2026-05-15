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
    private final Map<String, DefaultApi> defaultApiCache = new ConcurrentHashMap<>();

    public boolean checkUserProfile(FiscalCodePayload payload, String apiKeyUse) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.getProfileByPOST");
        //TODO: utilizzare defaultIoApi
        return true;
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
                        PnIoConnectorExceptionCodes.PN_IO_CONNECTOR_IO_RECIPIENT_NOT_FOUND, ex);
            } else if (status == 429) {
                throw new PnInternalException("IO rate limit exceeded",
                        PnIoConnectorExceptionCodes.PN_IO_CONNECTOR_IO_RATE_LIMIT, ex);
            } else {
                throw new PnInternalException("IO server error",
                        PnIoConnectorExceptionCodes.PN_IO_CONNECTOR_IO_SERVER_ERROR, ex);
            }
        }
    }

    public ExternalMessageResponseWithContent getMessageStatus(String fiscalCode, String ioMessageId, String apiKeyUse) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "defaultIoApi.getMessage");
        //TODO: utilizzare defaultIoApi
        return null;
    }

    public String getServiceUseKey(String serviceId, String apiKeyManage) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "manageAuthorizationIoApi.cmsGetServiceKeys");
        //TODO: utilizzare manageAuthorizationApi
        return null;
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
