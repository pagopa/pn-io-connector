package it.pagopa.pn.ioconnector.service;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.middleware.msclient.DataVaultClient;
import it.pagopa.pn.ioconnector.middleware.msclient.IoBackendClient;
import it.pagopa.pn.ioconnector.middleware.msclient.IoApiKeyResolver;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static it.pagopa.pn.ioconnector.utils.LogUtils.HANDLE_SEND_REQUEST;

@Service
@CustomLog
@RequiredArgsConstructor
public class IoMessageService {

    private final DataVaultClient dataVaultClient;
    private final IoBackendClient ioBackendClient;
    private final IoApiKeyResolver apiKeyResolver;

    public Mono<MessageResponse> handleSendRequest(MessageRequest request) {
        log.logStartingProcess(HANDLE_SEND_REQUEST);
        return MDCUtils.addMDCToContextAndExecute(
            Mono.fromRunnable(() -> MDC.put("requestId", request.getRequestId()))
                .then(
                    apiKeyResolver.resolveUseApiKey(request.getSenderTaxId(), request.getSenderServiceId())
                        .flatMap(apiKeyUse ->
                            dataVaultClient.deanonymize(request.getRecipientTaxId())
                                .flatMap(taxId -> ioBackendClient.checkUserProfile(taxId, apiKeyUse))
                                .flatMap(senderAllowed -> {
                                    if (!senderAllowed) {
                                        log.info("Profilo IO non abilitato — requestId={}", request.getRequestId());
                                        return Mono.just(buildResponse(request.getRequestId(), MessageResponse.StatusEnum.NOT_ACCEPTED));
                                    }
                                    // Mock mapping messaggio per coda sqs
                                    MessageSendRequest sqsMsg = MessageSendRequest.builder()
                                        .requestId(request.getRequestId())
                                        .iun(request.getIun())
                                        .recipientTaxId(request.getRecipientTaxId())
                                        .senderTaxId(request.getSenderTaxId())
                                        .senderServiceId(request.getSenderServiceId())
                                        .subject(request.getSubject())
                                        .markdown(request.getMarkdown())
                                        .attachments(request.getAttachments())
                                        .sensitiveContent(request.getSensitiveContent())
                                        .createdAt(Instant.now())
                                        .build();

                                    log.info(
                                        "Richiesta presa in carico — requestId={} iun={} senderServiceId={}",
                                        sqsMsg.getRequestId(),
                                        sqsMsg.getIun(),
                                        sqsMsg.getSenderServiceId()
                                    );
                                    return Mono.just(buildResponse(sqsMsg.getRequestId(), MessageResponse.StatusEnum.ACCEPTED));
                                })
                        )
                )
                .doOnError(throwable -> log.logEndingProcess(HANDLE_SEND_REQUEST, false, throwable.getMessage(), throwable))
                .doOnSuccess(result -> log.logEndingProcess(HANDLE_SEND_REQUEST))
        );
    }




    private MessageResponse buildResponse(String requestId, MessageResponse.StatusEnum status) {
        return new MessageResponse()
                .requestId(requestId)
                .status(status);
    }
}
