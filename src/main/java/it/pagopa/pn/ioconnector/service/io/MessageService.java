package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.ioconnector.model.EventType;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PaymentData;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;

import static it.pagopa.pn.ioconnector.utils.LogUtils.HANDLE_SEND_REQUEST;

@Service
@CustomLog
@RequiredArgsConstructor
public class MessageService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final PnIoConnectorConfig config;
    private final IOConnectorRequestDao requestDao;

    public Optional<MessageResponse> handleSendRequest(String cxId, MessageRequest request) {
        log.logStartingProcess(HANDLE_SEND_REQUEST);
        MDC.put("requestId", request.getRequestId());
        try {
            Optional<IOConnectorRequestEntity> existing = requestDao.findById(request.getRequestId());
            if (existing.isPresent()) {
                if (isSamePayload(cxId, request, existing.get())) {
                    log.info("Richiesta duplicata con payload identico — requestId={}", request.getRequestId());
                    return Optional.empty();
                } else {
                    throw new PnRuntimeException(
                        "Request ID already exists with different payload",
                        PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_REQUEST_CONFLICT,
                        HttpStatus.CONFLICT.value(),
                        new ArrayList<>()
                    );
                }
            }

            long pollingMaxHours = request.getPollingMaxHours() != null ? request.getPollingMaxHours() : 48;
            MessageSendRequest sqsMsg = MessageSendRequest.builder()
                .requestId(request.getRequestId())
                .xPagopaIoConCxId(cxId)
                .iun(request.getIun())
                .recipientTaxId(request.getRecipientTaxId())
                .senderServiceId(request.getSenderServiceId())
                .subject(request.getSubject())
                .markdown(request.getMarkdown())
                .attachments(request.getAttachments() != null
                    ? request.getAttachments().stream()
                        .map(a -> MessageSendRequest.Attachment.builder()
                            .id(a.getId())
                            .fileKey(a.getFileKey())
                            .build())
                        .collect(Collectors.toList())
                    : null)
                .sensitiveContent(request.getSensitiveContent())
                .dueDate(request.getDueDate())
                .paymentData(
                    request.getPaymentData() != null ?
                    MessageSendRequest.PaymentData.builder().
                        amount(request.getPaymentData().getAmount()).
                        noticeCode(request.getPaymentData().getNoticeCode()).
                        creditorTaxId(request.getPaymentData().getCreditorTaxId()).
                        invalidAfterDueDate(request.getPaymentData().getInvalidAfterDueDate()).
                        build() : null)
                .pollingMaxDate(Instant.now().plus(pollingMaxHours, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .build();

            log.info("Richiesta presa in carico — requestId={} iun={} senderServiceId={}",
                    sqsMsg.getRequestId(),
                    sqsMsg.getIun(),
                    sqsMsg.getSenderServiceId());

            String messageBody;
            try {
                messageBody = objectMapper.writeValueAsString(sqsMsg);
            } catch (JsonProcessingException e) {
                throw new PnInternalException("Failed to serialize SQS message",
                        PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_MESSAGE_SERIALIZATION_ERROR, e);
            }
            String queueUrl = sqsClient.getQueueUrl(r -> r.queueName(config.getSqsSendQueueName())).queueUrl();
            sqsClient.sendMessage(r -> r.queueUrl(queueUrl).messageBody(messageBody));

            requestDao.save(buildAcceptedEntity(cxId, sqsMsg, request));

            log.logEndingProcess(HANDLE_SEND_REQUEST);
            return Optional.of(new MessageResponse()
                .requestId(sqsMsg.getRequestId()).xPagopaIoConCxId(cxId)
                .status(MessageResponse.StatusEnum.ACCEPTED));

        } catch (Exception e) {
            log.logEndingProcess(HANDLE_SEND_REQUEST, false, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("requestId");
        }
    }

    private boolean isSamePayload(String cxId, MessageRequest request, IOConnectorRequestEntity entity) {
        return Objects.equals(cxId, entity.getXPagopaIoConCxId())
            && Objects.equals(request.getIun(), entity.getIun())
            && Objects.equals(request.getSenderServiceId(), entity.getSenderServiceId())
            && Objects.equals(request.getSubject(), entity.getSubject())
            && Objects.equals(request.getMarkdown(), entity.getMarkdown())
            && Objects.equals(request.getSensitiveContent(), entity.getSensitiveContent())
            && isSamePaymentData(request.getPaymentData(), entity.getPaymentData());
    }

    private boolean isSamePaymentData(PaymentData requestPd, IOConnectorRequestEntity.PaymentData entityPd) {
        if (requestPd == null && entityPd == null) return true;
        if (requestPd == null || entityPd == null) return false;
        return Objects.equals(requestPd.getAmount(), entityPd.getAmount())
            && Objects.equals(requestPd.getNoticeCode(), entityPd.getNoticeCode())
            && Objects.equals(requestPd.getCreditorTaxId(), entityPd.getCreditorTaxId());
    }

    private IOConnectorRequestEntity buildAcceptedEntity(String cxId, MessageSendRequest sqsMsg, MessageRequest request) {
        return IOConnectorRequestEntity.builder()
                .requestId(sqsMsg.getRequestId())
                .xPagopaIoConCxId(cxId)
                .iun(sqsMsg.getIun())
                .senderServiceId(sqsMsg.getSenderServiceId())
                .subject(sqsMsg.getSubject())
                .markdown(sqsMsg.getMarkdown())
                .sensitiveContent(sqsMsg.getSensitiveContent())
                .paymentData(sqsMsg.getPaymentData() != null
                        ? IOConnectorRequestEntity.PaymentData.builder()
                        .amount(sqsMsg.getPaymentData().getAmount())
                        .noticeCode(sqsMsg.getPaymentData().getNoticeCode())
                        .creditorTaxId(sqsMsg.getPaymentData().getCreditorTaxId())
                        .build()
                        : null)
                .attachments(sqsMsg.getAttachments() != null
                    ? sqsMsg.getAttachments().stream()
                        .map(a -> IOConnectorRequestEntity.Attachment.builder()
                            .id(a.getId())
                            .fileKey(a.getFileKey())
                            .build())
                        .collect(Collectors.toList())
                    : null)
                .status(EventType.ACCEPTED.name())
                .pollingMaxDate(Instant.now().plus(
                        request.getPollingMaxHours() != null ? request.getPollingMaxHours() : 48,
                        ChronoUnit.HOURS).toString())
                .eventList(List.of(
                        IOConnectorRequestEntity.Event.builder()
                                .eventDate(Instant.now().toString())
                                .status(EventType.ACCEPTED.name())
                                .build()
                ))
                .createdAt(Instant.now().toString())
                .build();
    }
}
