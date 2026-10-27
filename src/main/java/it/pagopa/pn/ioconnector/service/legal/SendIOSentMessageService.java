package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.api.dto.events.EventPublisher;
import it.pagopa.pn.api.dto.events.EventType;
import it.pagopa.pn.api.dto.events.MomProducer;
import it.pagopa.pn.api.dto.events.PnExtRegistryIOSentMessageEvent;
import it.pagopa.pn.api.dto.events.StandardEventHeader;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@CustomLog
@Service
@RequiredArgsConstructor
public class SendIOSentMessageService {

    static final String MESSAGE_GROUP_ID = "external-registry";
    static final String EVENT_ID_SEPARATOR = "_iosentmessage_";

    private final MomProducer<PnExtRegistryIOSentMessageEvent> ioSentMessageProducer;


    public void sendIOSentMessageNotification(String iun, int recIndex, String internalId, Instant sentDate) {
        log.info("Send IONotificationMessage event for iun={} recIndex={} internalId={} sentDate={}", iun, recIndex, internalId, sentDate);
        ioSentMessageProducer.push(buildIOSentMessageEvent(iun, recIndex, internalId, sentDate));
        log.info("Sent IONotificationMessage event for iun={} recIndex={}", iun, recIndex);
    }

    private PnExtRegistryIOSentMessageEvent buildIOSentMessageEvent(String iun, int recIndex, String internalId, Instant sentDate) {
        String eventId = iun + EVENT_ID_SEPARATOR + recIndex;
        return PnExtRegistryIOSentMessageEvent.builder()
                .messageDeduplicationId(eventId)
                .messageGroupId(MESSAGE_GROUP_ID)
                .header(StandardEventHeader.builder()
                        .iun(iun)
                        .eventId(eventId)
                        .createdAt(Instant.now())
                        .eventType(EventType.SEND_IO_MESSAGE_REQUEST.name())
                        .publisher(EventPublisher.EXTERNAL_REGISTRY.name())
                        .build())
                .payload(PnExtRegistryIOSentMessageEvent.Payload.builder()
                        .iun(iun)
                        .recIndex(recIndex)
                        .internalId(internalId)
                        .sendDate(sentDate)
                        .build())
                .build();
    }
}
