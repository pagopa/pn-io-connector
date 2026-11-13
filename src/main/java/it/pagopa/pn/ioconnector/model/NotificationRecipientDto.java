package it.pagopa.pn.ioconnector.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationRecipientDto {
    private String internalId;
}
