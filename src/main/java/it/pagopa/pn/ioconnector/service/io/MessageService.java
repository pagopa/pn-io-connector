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

    public MessageResponse handleSendRequest(String cxId, MessageRequest request) {
        log.logStartingProcess(HANDLE_SEND_REQUEST);
        MDC.put("requestId", request.getRequestId());
        try {

            MessageSendRequest sqsMsg = MessageSendRequest.builder()
                .requestId(request.getRequestId())
                .xPagopaIoConCxId(cxId)
                .iun(request.getIun())
                .recipientTaxId(request.getRecipientTaxId())
                .senderServiceId(request.getSenderServiceId())
                .subject(request.getSubject())
                .markdown(request.getMarkdown())
                .attachments(request.getAttachments())
                .sensitiveContent(request.getSensitiveContent())
                .dueDate(request.getDueDate())
                .paymentData(
                    request.getPaymentData() != null ?
                    MessageSendRequest.PaymentData.builder().
                        amount(request.getPaymentData().getAmount()).
                        noticeCode(request.getPaymentData().getNoticeCode()).
                        invalidAfterDueDate(request.getPaymentData().getInvalidAfterDueDate()).
                        build() : null)
                .createdAt(Instant.now())
                .build();

            log.info("Richiesta presa in carico — requestId={} iun={} senderServiceId={}",
                    sqsMsg.getRequestId(),
                    sqsMsg.getIun(),
                    sqsMsg.getSenderServiceId());

            log.logEndingProcess(HANDLE_SEND_REQUEST);
            return new MessageResponse()
                .requestId(sqsMsg.getRequestId()).xPagopaIoConCxId(cxId)
                .status(MessageResponse.StatusEnum.ACCEPTED);

        } catch (Exception e) {
            log.logEndingProcess(HANDLE_SEND_REQUEST, false, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("requestId");
        }
    }
}
