package it.pagopa.pn.ioconnector.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotificationDto {
    private String iun;
    private String subject;
    private String senderDenomination;
    private List<NotificationRecipientDto> recipients;
}
