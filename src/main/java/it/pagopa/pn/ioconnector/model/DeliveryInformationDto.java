package it.pagopa.pn.ioconnector.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DeliveryInformationDto {

    private String iun;
    private String deliveryMode;
    private Instant schedulingAnalogDate;
    private Instant refinementOrViewedDate;
    private Boolean isNotificationCancelled;
    private Boolean isNotificationAccepted;
}
