package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.api.dto.events.MomProducer;
import it.pagopa.pn.api.dto.events.PnExtRegistryIOSentMessageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SendIOSentMessageServiceTest {

    private static final String IUN = "IUN-001";
    private static final int REC_INDEX = 1;
    private static final String INTERNAL_ID = "PF-internal-001";
    private static final Instant SENT_DATE = Instant.parse("2026-03-01T10:15:30.123Z");

    @Mock
    private MomProducer<PnExtRegistryIOSentMessageEvent> ioSentMessageProducer;

    private SendIOSentMessageService service;

    @BeforeEach
    void setUp() {
        service = new SendIOSentMessageService(ioSentMessageProducer);
    }

    @Test
    void sendIOSentMessageNotification_pushesEventLikeExtRegistries() {
        Instant before = Instant.now();

        service.sendIOSentMessageNotification(IUN, REC_INDEX, INTERNAL_ID, SENT_DATE);

        ArgumentCaptor<PnExtRegistryIOSentMessageEvent> captor = ArgumentCaptor.forClass(PnExtRegistryIOSentMessageEvent.class);
        verify(ioSentMessageProducer).push(captor.capture());
        PnExtRegistryIOSentMessageEvent event = captor.getValue();

        assertThat(event.getMessageDeduplicationId()).isEqualTo("IUN-001_iosentmessage_1");
        assertThat(event.getMessageGroupId()).isEqualTo("external-registry");

        assertThat(event.getHeader().getIun()).isEqualTo(IUN);
        assertThat(event.getHeader().getEventId()).isEqualTo("IUN-001_iosentmessage_1");
        assertThat(event.getHeader().getEventType()).isEqualTo("SEND_IO_MESSAGE_REQUEST");
        assertThat(event.getHeader().getPublisher()).isEqualTo("EXTERNAL_REGISTRY");
        assertThat(event.getHeader().getCreatedAt()).isBetween(before, Instant.now());

        assertThat(event.getPayload().getIun()).isEqualTo(IUN);
        assertThat(event.getPayload().getRecIndex()).isEqualTo(REC_INDEX);
        assertThat(event.getPayload().getInternalId()).isEqualTo(INTERNAL_ID);
        assertThat(event.getPayload().getSendDate()).isEqualTo(SENT_DATE);
    }

    @Test
    void sendIOSentMessageNotification_producerError_isPropagated() {
        doThrow(new IllegalStateException("SQS unavailable"))
                .when(ioSentMessageProducer).push(any(PnExtRegistryIOSentMessageEvent.class));

        assertThatThrownBy(() -> service.sendIOSentMessageNotification(IUN, REC_INDEX, INTERNAL_ID, SENT_DATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SQS unavailable");
    }
}
