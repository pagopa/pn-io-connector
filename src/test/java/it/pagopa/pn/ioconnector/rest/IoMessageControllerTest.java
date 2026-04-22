package it.pagopa.pn.ioconnector.rest;

import static org.junit.jupiter.api.Assertions.*;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.service.IoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = IoMessageController.class)
@Import(IoMessageService.class)
class IoMessageControllerTest {


    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IoMessageService ioMessageService;

    @Test
    void sendIOMessageAccepted() {
        MessageResponse response = new MessageResponse()
                .requestId("REQ-TEST-001")
                .status(MessageResponse.StatusEnum.ACCEPTED);

        when(ioMessageService.handleSendRequest(any(MessageRequest.class)))
                .thenReturn(Mono.just(response));

        webTestClient.post()
                .uri("/io/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequest())
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(MessageResponse.class)
                .value(r -> {
                    assertEquals("REQ-TEST-001", r.getRequestId());
                    assertEquals(MessageResponse.StatusEnum.ACCEPTED, r.getStatus());
                });
    }

    @Test
    void sendIOMessageNotAccepted() {
        MessageResponse response = new MessageResponse()
                .requestId("REQ-TEST-001")
                .status(MessageResponse.StatusEnum.NOT_ACCEPTED);

        when(ioMessageService.handleSendRequest(any(MessageRequest.class)))
                .thenReturn(Mono.just(response));

        webTestClient.post()
                .uri("/io/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequest())
                .exchange()
                .expectStatus().isOk()
                .expectBody(MessageResponse.class)
                .value(r -> {
                    assertEquals("REQ-TEST-001", r.getRequestId());
                    assertEquals(MessageResponse.StatusEnum.NOT_ACCEPTED, r.getStatus());
                });
    }

    @Test
    void sendIOMessageMissingRequiredFields() {
        MessageRequest invalidRequest = new MessageRequest()
                .requestId("REQ-TEST-002");

        webTestClient.post()
                .uri("/io/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private MessageRequest buildRequest() {
        return new MessageRequest()
                .requestId("REQ-TEST-001")
                .iun("ABCD-EFGH-1234-5678-X")
                .recipientTaxId("ANON123456789")
                .senderTaxId("12345678901")
                .senderServiceId("000000000")
                .subject("Notifica di test")
                .markdown("Hai ricevuto una notifica di test.");
    }
}
