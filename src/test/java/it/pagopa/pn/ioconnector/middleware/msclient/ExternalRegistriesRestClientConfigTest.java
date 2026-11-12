package it.pagopa.pn.ioconnector.middleware.msclient;

import com.sun.net.httpserver.HttpServer;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.externalregistries.v1.api.PrivacyNoticeApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalRegistriesRestClientConfigTest {

    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            byte[] body = "{\"version\":3}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void privacyNoticeApi_usesConfiguredBaseUrl() {
        PnIoConnectorConfig config = new PnIoConnectorConfig();
        config.setExternalRegistriesBaseUrl("http://localhost:" + server.getAddress().getPort());
        MsClientConfig.ExternalRegistriesApis apis = new MsClientConfig.ExternalRegistriesApis();
        ReflectionTestUtils.setField(apis, "restClientWithTracing", RestClient.builder().build());

        PrivacyNoticeApi api = apis.privacyNoticeApi(config);

        assertThat(api.findPrivacyNoticeVersion("TOS", "PF").getVersion()).isEqualTo(3);
        assertThat(requestPath.get()).isEqualTo("/ext-registry-private/privacynotice/TOS/PF");
    }
}
