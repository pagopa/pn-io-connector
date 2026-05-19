package it.pagopa.pn.ioconnector.middleware.queue.consumer;

import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import it.pagopa.pn.ioconnector.service.eventbridge.EventBridgeProducer;
import it.pagopa.pn.ioconnector.service.sqs.PollingQueueProducer;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.service.io.IOService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@CustomLog
@RequiredArgsConstructor
public class SendWorker {

    private final IOService ioService;
    private final IOConnectorRequestDao dao;
    private final EventBridgeProducer eventBridgeProducer;
    private final PollingQueueProducer pollingQueueProducer;

    @SqsListener(value = "${pn.io-connector.sqs-send-queue-name}")
    public void process(MessageSendRequest request) {
        String apiKey = ioService.getServiceUseKey(request.getSenderTaxId(), request.getSenderServiceId());

        LimitedProfile profile;
        try {
            profile = ioService.checkUserProfile(request.getRecipientTaxId(), apiKey);
        } catch (PnHttpResponseException e) {
            if (e.getStatusCode() == 404) {
                handleSenderNotAllowed(request);
                return;
            }
            throw e;
        }

        if (Boolean.FALSE.equals(profile.getSenderAllowed())) {
            handleSenderNotAllowed(request);
            return;
        }

        String ioMessageId = ioService.sendMessage(request, apiKey);

        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .ioMessageId(ioMessageId)
                .status(EventType.SENT_TO_IO.name())
                .build();
        dao.update(entity);

        OutcomeEvent outcomeEvent = OutcomeEvent.builder()
                .requestId(request.getRequestId())
                .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                .ioMessageId(ioMessageId)
                .eventType(EventType.SENT_TO_IO)
                .eventTimestamp(Instant.now())
                .build();
        eventBridgeProducer.publish(outcomeEvent);

        OutcomePollingRequest pollingRequest = OutcomePollingRequest.builder()
                .requestId(request.getRequestId())
                .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                .iun(request.getIun())
                .recipientTaxId(request.getRecipientTaxId())
                .ioMessageId(ioMessageId)
                .lastKnownStatus(EventType.SENT_TO_IO)
                .paymentData(request.getPaymentData() != null)
                .pollingMaxDate(request.getPollingMaxDate())
                .build();
        pollingQueueProducer.publish(pollingRequest);
    }

    private void handleSenderNotAllowed(MessageSendRequest request) {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId(request.getRequestId())
                .status(EventType.SENDER_NOT_ALLOWED.name())
                .build();
        dao.update(entity);

        OutcomeEvent outcomeEvent = OutcomeEvent.builder()
                .requestId(request.getRequestId())
                .xPagopaIoConCxId(request.getXPagopaIoConCxId())
                .eventType(EventType.SENDER_NOT_ALLOWED)
                .eventTimestamp(Instant.now())
                .build();
        eventBridgeProducer.publish(outcomeEvent);
    }
}
