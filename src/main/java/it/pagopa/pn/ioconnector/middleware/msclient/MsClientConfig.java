package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.api.RecipientsApi;
import it.pagopa.pn.ioconnector.middleware.msclient.common.BaseRestClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MsClientConfig {

    @Configuration
    static class DataVaultApis extends BaseRestClient {

        @Bean
        RecipientsApi recipientsApi(PnIoConnectorConfig config) {
            var apiClient = new it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.ApiClient(
                    initRestClient().build()
            );
            apiClient.setBasePath(config.getDataVaultBaseUrl());
            return new RecipientsApi(apiClient);
        }
    }
}
