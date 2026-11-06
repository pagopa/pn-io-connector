package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class BottomSheetProcessorFactoryTest {

    @Mock
    private DigitalDisclaimerProcessor digitalDisclaimerProcessor;

    @Mock
    private AnalogDisclaimerProcessor analogDisclaimerProcessor;

    @Mock
    private DigitalViewedDisclaimerProcessor digitalViewedDisclaimerProcessor;

    @Mock
    private AnalogViewedDisclaimerProcessor analogViewedDisclaimerProcessor;

    @Mock
    private CancelledDisclaimerProcessor cancelledDisclaimerProcessor;

    @InjectMocks
    private BottomSheetProcessorFactory factory;

    @Test
    void getProcessor_WhenCancelled_ReturnsCancelledProcessor() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .isNotificationCancelled(true)
                .deliveryMode("DIGITAL")
                .build();

        BottomSheetProcessor result = factory.getProcessor(dto);

        assertThat(result).isSameAs(cancelledDisclaimerProcessor);
    }

    @Test
    void getProcessor_WhenDigitalAndRefinementDatePast_ReturnsDigitalViewedProcessor() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .deliveryMode("DIGITAL")
                .refinementOrViewedDate(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        BottomSheetProcessor result = factory.getProcessor(dto);

        assertThat(result).isSameAs(digitalViewedDisclaimerProcessor);
    }

    @Test
    void getProcessor_WhenAnalogAndSchedulingDatePast_ReturnsAnalogViewedProcessor() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .deliveryMode("ANALOG")
                .schedulingAnalogDate(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        BottomSheetProcessor result = factory.getProcessor(dto);

        assertThat(result).isSameAs(analogViewedDisclaimerProcessor);
    }

    @Test
    void getProcessor_WhenAnalogAndSchedulingDateFuture_ReturnsAnalogProcessor() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .deliveryMode("ANALOG")
                .schedulingAnalogDate(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        BottomSheetProcessor result = factory.getProcessor(dto);

        assertThat(result).isSameAs(analogDisclaimerProcessor);
    }

    @Test
    void getProcessor_WhenDigitalAndNoRefinementDate_ReturnsDigitalProcessor() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .deliveryMode("DIGITAL")
                .build();

        BottomSheetProcessor result = factory.getProcessor(dto);

        assertThat(result).isSameAs(digitalDisclaimerProcessor);
    }

    @Test
    void getProcessor_WhenUnknownDeliveryMode_ThrowsPnInternalException() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .deliveryMode("UNKNOWN")
                .build();

        assertThatThrownBy(() -> factory.getProcessor(dto))
                .isInstanceOf(PnInternalException.class);
    }

    @Test
    void getProcessor_WhenNotCancelledFlagFalse_DoesNotReturnCancelledProcessor() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .isNotificationCancelled(false)
                .deliveryMode("DIGITAL")
                .build();

        BottomSheetProcessor result = factory.getProcessor(dto);

        assertThat(result).isSameAs(digitalDisclaimerProcessor);
    }
}
