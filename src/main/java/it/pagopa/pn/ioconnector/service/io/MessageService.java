package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
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

    private final ProfileService profileService;

    public MessageResponse handleSendRequest(String cxId, MessageRequest request) {
        log.logStartingProcess(HANDLE_SEND_REQUEST);
        MDC.put("requestId", request.getRequestId());
        try {
            boolean senderAllowed = profileService.resolveProfile(
                request.getSenderTaxId(), request.getSenderServiceId(), request.getRecipientTaxId()
            );

            if (!senderAllowed) {
                log.info("Profilo IO non abilitato — requestId={}", request.getRequestId());
                log.logEndingProcess(HANDLE_SEND_REQUEST);
                return new MessageResponse()
                    .requestId(request.getRequestId()).cxId(cxId)
                    .status(MessageResponse.StatusEnum.NOT_ACCEPTED);
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
            return new MessageResponse()
                .requestId(sqsMsg.getRequestId()).cxId(cxId)
                .status(MessageResponse.StatusEnum.ACCEPTED);

        } catch (Exception e) {
            log.logEndingProcess(HANDLE_SEND_REQUEST, false, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("requestId");
        }
    }
}
