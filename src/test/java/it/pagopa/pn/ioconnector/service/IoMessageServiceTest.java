package it.pagopa.pn.ioconnector.service;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.middleware.msclient.DataVaultClient;
import it.pagopa.pn.ioconnector.middleware.msclient.IoApiKeyResolver;
import it.pagopa.pn.ioconnector.middleware.msclient.IoBackendClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class IoMessageServiceTest {

    @Mock private DataVaultClient dataVaultClient;
    @Mock private IoBackendClient ioBackendClient;
    @Mock private IoApiKeyResolver apiKeyResolver;

    @InjectMocks private IoMessageService service;

    @Test
    void handleSendRequest_accepted() {
        when(apiKeyResolver.resolveUseApiKey("SENDER-TAX", "SVC-001")).thenReturn(Mono.just("key"));
        when(dataVaultClient.deanonymize("ANON-TAX")).thenReturn(Mono.just("CF"));
        when(ioBackendClient.checkUserProfile("CF", "key")).thenReturn(Mono.just(true));

        StepVerifier.create(service.handleSendRequest(buildRequest()))
                .expectNextMatches(r ->
                        r.getStatus() == MessageResponse.StatusEnum.ACCEPTED &&
                        "REQ-001".equals(r.getRequestId()))
                .verifyComplete();
    }

    @Test
    void handleSendRequest_notAccepted() {
        when(apiKeyResolver.resolveUseApiKey("SENDER-TAX", "SVC-001")).thenReturn(Mono.just("key"));
        when(dataVaultClient.deanonymize("ANON-TAX")).thenReturn(Mono.just("CF"));
        when(ioBackendClient.checkUserProfile("CF", "key")).thenReturn(Mono.just(false));

        StepVerifier.create(service.handleSendRequest(buildRequest()))
                .expectNextMatches(r ->
                        r.getStatus() == MessageResponse.StatusEnum.NOT_ACCEPTED &&
                        "REQ-001".equals(r.getRequestId()))
                .verifyComplete();
    }

    private MessageRequest buildRequest() {
        return new MessageRequest()
                .requestId("REQ-001")
                .iun("IUN-001")
                .recipientTaxId("ANON-TAX")
                .senderTaxId("SENDER-TAX")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body");
    }
}
