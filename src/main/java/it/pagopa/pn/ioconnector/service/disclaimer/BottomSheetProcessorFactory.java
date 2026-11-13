package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@CustomLog
@Component
@RequiredArgsConstructor
public class BottomSheetProcessorFactory {

    private final DigitalDisclaimerProcessor digitalDisclaimerProcessor;
    private final AnalogDisclaimerProcessor analogDisclaimerProcessor;
    private final DigitalViewedDisclaimerProcessor digitalViewedDisclaimerProcessor;
    private final AnalogViewedDisclaimerProcessor analogViewedDisclaimerProcessor;
    private final CancelledDisclaimerProcessor cancelledDisclaimerProcessor;

    public BottomSheetProcessor getProcessor(DeliveryInformationDto deliveryInfo) {
        Instant now = Instant.now();

        if (Boolean.TRUE.equals(deliveryInfo.getIsNotificationCancelled())) {
            return cancelledDisclaimerProcessor;
        }
        if (deliveryInfo.getRefinementOrViewedDate() != null &&
                now.isAfter(deliveryInfo.getRefinementOrViewedDate())) {
            return digitalViewedDisclaimerProcessor;
        }
        if ("ANALOG".equals(deliveryInfo.getDeliveryMode()) &&
                deliveryInfo.getSchedulingAnalogDate() != null &&
                now.isAfter(deliveryInfo.getSchedulingAnalogDate())) {
            return analogViewedDisclaimerProcessor;
        }
        if ("ANALOG".equals(deliveryInfo.getDeliveryMode()) &&
                deliveryInfo.getSchedulingAnalogDate() != null &&
                now.isBefore(deliveryInfo.getSchedulingAnalogDate())) {
            return analogDisclaimerProcessor;
        }
        if ("DIGITAL".equals(deliveryInfo.getDeliveryMode())) {
            return digitalDisclaimerProcessor;
        }
        throw new PnInternalException(
                "No processor found for deliveryMode: " + deliveryInfo.getDeliveryMode(),
                "BOTTOM_SHEET_PROCESSOR_NOT_FOUND"
        );
    }
}
