package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.ioconnector.config.IoLegalSecrets;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.api.RecipientsApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.DefaultApi;
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

    /**
     * Client per le comunicazioni a valore legale
     */
    @Configuration
    static class IoLegalApis extends BaseRestClient {

        /** Lettura profilo, messaggi di cortesia e activations. */
        @Bean
        DefaultApi ioLegalApi(PnIoConnectorConfig config, IoLegalSecrets secrets) {
            return buildDefaultApi(config, secrets.ioApiKey());
        }

        /** Messaggi di opt-in. */
        @Bean
        DefaultApi ioOptInApi(PnIoConnectorConfig config, IoLegalSecrets secrets) {
            return buildDefaultApi(config, secrets.ioActApiKey());
        }

        private DefaultApi buildDefaultApi(PnIoConnectorConfig config, String apiKey) {
            var apiClient = new it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.ApiClient(
                    initRestClient(apiKey).build()
            );
            apiClient.setBasePath(config.getIoLegalBaseUrl());
            return new DefaultApi(apiClient);
        }
    }
}
