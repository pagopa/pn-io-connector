package it.pagopa.pn.ioconnector.rest;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.api.MessageApi;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.service.IoMessageService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@CustomLog
@RestController
@RequiredArgsConstructor
public class IoMessageController implements MessageApi {

    private final IoMessageService ioMessageService;

    @Override
    public Mono<ResponseEntity<MessageResponse>> sendIOMessage(
            Mono<MessageRequest> messageRequest,
            ServerWebExchange exchange
    ) {
        return messageRequest.flatMap(
                request -> ioMessageService.handleSendRequest(request)
                .map(response -> MessageResponse.StatusEnum.ACCEPTED.equals(response.getStatus())
                        ? ResponseEntity.accepted().body(response)
                        : ResponseEntity.ok(response)));
    }
}
