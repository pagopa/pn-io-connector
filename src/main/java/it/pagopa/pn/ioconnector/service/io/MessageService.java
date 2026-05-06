package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.service.DataVaultService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static it.pagopa.pn.ioconnector.utils.LogUtils.HANDLE_SEND_REQUEST;

@Service
@CustomLog
@RequiredArgsConstructor
public class MessageService {

    private final IOService ioService;
    private final DataVaultService dataVaultService;

    public MessageResponse handleSendRequest(String cxId, MessageRequest request) {
        log.logStartingProcess(HANDLE_SEND_REQUEST);
        MDC.put("cxId", cxId);
        MDC.put("requestId", request.getRequestId());
        try {
            String apiKeyUse = ioService.getServiceUseKey(
                request.getSenderTaxId(), request.getSenderServiceId()
            );
            String taxId = dataVaultService.deanonymize(request.getRecipientTaxId());
            boolean senderAllowed = ioService.checkUserProfile(taxId, apiKeyUse);

            if (!senderAllowed) {
                log.info("Profilo IO non abilitato — requestId={}", request.getRequestId());
                log.logEndingProcess(HANDLE_SEND_REQUEST);
                return new MessageResponse().requestId(request.getRequestId()).cxId(cxId).status(MessageResponse.StatusEnum.NOT_ACCEPTED);
            }

            MessageSendRequest sqsMsg = MessageSendRequest.builder()
                    .requestId(request.getRequestId())
                    .cxId(cxId)
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

            log.info("Richiesta presa in carico — requestId={} iun={} senderServiceId={}",
                    sqsMsg.getRequestId(),
                    sqsMsg.getIun(),
                    sqsMsg.getSenderServiceId());

            log.logEndingProcess(HANDLE_SEND_REQUEST);
            return new MessageResponse().requestId(sqsMsg.getRequestId()).cxId(cxId).status(MessageResponse.StatusEnum.ACCEPTED);

        } catch (Exception e) {
            log.logEndingProcess(HANDLE_SEND_REQUEST, false, e.getMessage(), e);
            throw e;
        }
    }
}
