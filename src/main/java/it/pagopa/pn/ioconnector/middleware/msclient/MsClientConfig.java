package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.api.RecipientsApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.ManageAuthorizationApi;
import it.pagopa.pn.ioconnector.middleware.msclient.common.BaseRestClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MsClientConfig {

    @Configuration
    static class IOApis extends BaseRestClient {

        @Bean
        ManageAuthorizationApi manageAuthorizationApi(PnIoConnectorConfig config) {
            var apiClient = new it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.ApiClient(
                    initRestClient("ApiKey_MANAGE") //TODO: get ApiKey MANAGE
                            .baseUrl(config.getIoBaseUrl())
                            .build()
            );
            return new ManageAuthorizationApi(apiClient);
        }
    }

    @Configuration
    static class DataVaultApis extends BaseRestClient {

        @Bean
        RecipientsApi recipientsApi(PnIoConnectorConfig config) {
            var apiClient = new it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.ApiClient(
                    initRestClient()
                            .baseUrl(config.getDataVaultBaseUrl())
                            .build()
            );
            return new RecipientsApi(apiClient);
        }
    }
}
