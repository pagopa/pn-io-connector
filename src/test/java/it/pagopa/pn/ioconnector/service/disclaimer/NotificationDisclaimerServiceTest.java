package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import it.pagopa.pn.ioconnector.middleware.msclient.TimelineClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private BottomSheetProcessorFactory bottomSheetProcessorFactory;

    @InjectMocks
    private NotificationDisclaimerService service;

    @Test
    void getNotificationDisclaimer_WhenTimelineReturnsDigital_ThenReturnsPreconditionContent() {
        DeliveryInformationDto deliveryInfo = DeliveryInformationDto.builder()
                .deliveryMode("DIGITAL")
                .build();
        PreconditionContent expected = new PreconditionContent("Titolo", "Contenuto markdown");
        BottomSheetProcessor processor = dto -> expected;

        when(timelineClient.getDeliveryInformation(eq(IUN), anyInt())).thenReturn(deliveryInfo);
        when(bottomSheetProcessorFactory.getProcessor(any())).thenReturn(processor);

        PreconditionContent result = service.getNotificationDisclaimer(IUN, CX_ID);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getNotificationDisclaimer_WhenTimelineReturnsUnknown_ThenThrows404() {
        DeliveryInformationDto deliveryInfo = DeliveryInformationDto.builder()
                .deliveryMode("UNKNOWN")
                .build();

        when(timelineClient.getDeliveryInformation(eq(IUN), anyInt())).thenReturn(deliveryInfo);

        PnHttpResponseException ex = assertThrows(PnHttpResponseException.class,
                () -> service.getNotificationDisclaimer(IUN, CX_ID));
        assertThat(ex.getStatusCode()).isEqualTo(404);
    }

    @Test
    void getNotificationDisclaimer_WhenTimelineReturns404_ThenThrows404() {
        when(timelineClient.getDeliveryInformation(eq(IUN), anyInt()))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        assertThrows(PnHttpResponseException.class,
                () -> service.getNotificationDisclaimer(IUN, CX_ID));
    }
}
