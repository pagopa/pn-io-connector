package it.pagopa.pn.ioconnector.middleware.msclient.common;

import it.pagopa.pn.commons.pnclients.RestClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@Import(RestClientFactory.class)
public abstract class BaseRestClient {

    private static final String HEADER_API_KEY = "Ocp-Apim-Subscription-Key";

    @Autowired
    @Qualifier("withTracing")
    private RestClient restClientWithTracing;

    protected RestClient.Builder initRestClient(String apiKey) {
        return restClientWithTracing.mutate().defaultHeader(HEADER_API_KEY, apiKey);
    }

    protected RestClient.Builder initRestClient() {
        return restClientWithTracing.mutate();
    }
}
