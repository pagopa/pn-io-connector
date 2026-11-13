package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.middleware.msclient.DeliveryClient;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import it.pagopa.pn.ioconnector.model.NotificationDto;
import it.pagopa.pn.ioconnector.model.NotificationRecipientDto;
import it.pagopa.pn.ioconnector.middleware.msclient.TimelineClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDisclaimerServiceTest {

    private static final String IUN = "TEST-IUN-001-202610";
    private static final String CX_ID = "cx-id-001";

    @Mock
    private TimelineClient timelineClient;

    @Mock
    private DeliveryClient deliveryClient;

    @Mock
    private BottomSheetProcessorFactory bottomSheetProcessorFactory;

    @Mock
    private PnIoConnectorConfig cfg;

    @InjectMocks
    private NotificationDisclaimerService service;

    private NotificationDto notificationWithRecipient() {
        return NotificationDto.builder()
                .senderDenomination("Test PA")
                .subject("Test Subject")
                .recipients(List.of(NotificationRecipientDto.builder().internalId(CX_ID).build()))
                .build();
    }

    @Test
    void getNotificationDisclaimer_WhenTimelineReturnsDigital_ThenReturnsPreconditionContent() {
        DeliveryInformationDto deliveryInfo = DeliveryInformationDto.builder()
                .deliveryMode("DIGITAL")
                .build();
        PreconditionContent expected = new PreconditionContent("Titolo", "Contenuto markdown");
        BottomSheetProcessor processor = dto -> expected;

        when(deliveryClient.getSentNotification(IUN)).thenReturn(notificationWithRecipient());
        when(timelineClient.getDeliveryInformation(eq(IUN), eq(0))).thenReturn(deliveryInfo);
        when(bottomSheetProcessorFactory.getProcessor(any())).thenReturn(processor);

        PreconditionContent result = service.getNotificationDisclaimer(IUN, CX_ID);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getNotificationDisclaimer_WhenTimelineReturnsUnknown_ThenThrows404() {
        DeliveryInformationDto deliveryInfo = DeliveryInformationDto.builder()
                .deliveryMode("UNKNOWN")
                .build();

        when(deliveryClient.getSentNotification(IUN)).thenReturn(notificationWithRecipient());
        when(timelineClient.getDeliveryInformation(eq(IUN), eq(0))).thenReturn(deliveryInfo);

        PnHttpResponseException ex = assertThrows(PnHttpResponseException.class,
                () -> service.getNotificationDisclaimer(IUN, CX_ID));
        assertThat(ex.getStatusCode()).isEqualTo(404);
    }

    @Test
    void getNotificationDisclaimer_WhenTimelineReturns404_ThenThrows404() {
        when(deliveryClient.getSentNotification(IUN)).thenReturn(notificationWithRecipient());
        when(timelineClient.getDeliveryInformation(eq(IUN), eq(0)))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        assertThrows(PnHttpResponseException.class,
                () -> service.getNotificationDisclaimer(IUN, CX_ID));
    }

    @Test
    void getNotificationDisclaimer_WhenMarkdownContainsPlaceholders_ThenResolvesUrls() {
        DeliveryInformationDto deliveryInfo = DeliveryInformationDto.builder()
                .deliveryMode("DIGITAL")
                .build();
        String rawMarkdown = "Privacy: ${piattaformaNotificheURLPrivacy} TOS: ${piattaformaNotificheURLTOS}";
        BottomSheetProcessor processor = dto -> new PreconditionContent("Titolo", rawMarkdown);

        when(deliveryClient.getSentNotification(IUN)).thenReturn(notificationWithRecipient());
        when(timelineClient.getDeliveryInformation(eq(IUN), eq(0))).thenReturn(deliveryInfo);
        when(bottomSheetProcessorFactory.getProcessor(any())).thenReturn(processor);
        when(cfg.getPiattaformanotificheurlPrivacy()).thenReturn("https://privacy.example.com");
        when(cfg.getPiattaformanotificheurlTos()).thenReturn("https://tos.example.com");

        PreconditionContent result = service.getNotificationDisclaimer(IUN, CX_ID);

        assertThat(result.getMarkdown()).isEqualTo("Privacy: https://privacy.example.com TOS: https://tos.example.com");
    }

    @Test
    void getNotificationDisclaimer_WhenCxIdNotAmongRecipients_ThenThrows404() {
        NotificationDto notification = NotificationDto.builder()
                .recipients(List.of(NotificationRecipientDto.builder().internalId("other-cx-id").build()))
                .build();

        when(deliveryClient.getSentNotification(IUN)).thenReturn(notification);

        PnHttpResponseException ex = assertThrows(PnHttpResponseException.class,
                () -> service.getNotificationDisclaimer(IUN, CX_ID));
        assertThat(ex.getStatusCode()).isEqualTo(404);
    }
}
