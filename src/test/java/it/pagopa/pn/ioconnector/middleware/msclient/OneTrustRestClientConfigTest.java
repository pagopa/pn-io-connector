package it.pagopa.pn.ioconnector.middleware.msclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.pnclients.RestClientFactory;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OneTrustRestClientConfigTest {

    private static final int CONNECT_TIMEOUT_MILLIS = 1000;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int responseStatus = 200;
    private volatile long responseDelayMillis = 0;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try {
                Thread.sleep(responseDelayMillis);
                exchange.sendResponseHeaders(responseStatus, -1);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private RestClient buildClient(int readTimeoutMillis, int retryMaxAttempts) {
        PnIoConnectorConfig config = new PnIoConnectorConfig();
        config.setOneTrustBaseUrl("http://localhost:" + server.getAddress().getPort());
        config.setOneTrustReadTimeoutMillis(readTimeoutMillis);
        config.setOneTrustRetryMaxAttempts(retryMaxAttempts);
        return new MsClientConfig.OneTrustApis().oneTrustRestClient(
                config, new RestClientFactory(), RestClient.builder(), new ObjectMapper(), CONNECT_TIMEOUT_MILLIS);
    }

    @Test
    void oneTrustRestClient_usesConfiguredBaseUrl() {
        RestClient client = buildClient(3000, 1);

        client.get().uri("/ping").retrieve().toBodilessEntity();

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void oneTrustRestClient_serverError_retriesConfiguredNumberOfTimes() {
        responseStatus = 503;
        RestClient client = buildClient(3000, 1);

        assertThatThrownBy(() -> client.get().uri("/ping").retrieve().toBodilessEntity())
                .isInstanceOf(PnHttpResponseException.class);

        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void oneTrustRestClient_noRetryConfigured_callsOnce() {
        responseStatus = 503;
        RestClient client = buildClient(3000, 0);

        assertThatThrownBy(() -> client.get().uri("/ping").retrieve().toBodilessEntity())
                .isInstanceOf(PnHttpResponseException.class);

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void oneTrustRestClient_responseSlowerThanReadTimeout_failsWithTimeout() {
        responseDelayMillis = 1500;
        RestClient client = buildClient(200, 0);

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> client.get().uri("/ping").retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);

        assertThat(System.currentTimeMillis() - start).isLessThan(1400);
    }
}
