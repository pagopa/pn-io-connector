package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.timelineservice.v1.api.TimelineControllerApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.timelineservice.v1.dto.DeliveryInformationResponse;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@CustomLog
@Component
@RequiredArgsConstructor
public class TimelineClient {

    private final TimelineControllerApi timelineControllerApi;

    public DeliveryInformationDto getDeliveryInformation(String iun, Integer recIndex) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.PN_TIMELINE_SERVICE, "getDeliveryInformation");
        DeliveryInformationResponse response = timelineControllerApi.getDeliveryInformation(iun, recIndex);
        return DeliveryInformationDto.builder()
                .deliveryMode(response.getDeliveryMode() != null ? response.getDeliveryMode().getValue() : null)
                .schedulingAnalogDate(response.getSchedulingAnalogDate() != null ? response.getSchedulingAnalogDate().toInstant() : null)
                .refinementOrViewedDate(response.getRefinementOrViewedDate() != null ? response.getRefinementOrViewedDate().toInstant() : null)
                .isNotificationCancelled(response.getIsNotificationCancelled())
                .isNotificationAccepted(response.getIsNotificationAccepted())
                .build();
    }
}
