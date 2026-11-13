package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.delivery.v1.api.InternalOnlyApi;
import it.pagopa.pn.ioconnector.model.NotificationDto;
import it.pagopa.pn.ioconnector.model.NotificationRecipientDto;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@CustomLog
@Component
@RequiredArgsConstructor
public class DeliveryClient {

    private final InternalOnlyApi internalOnlyApi;

    public NotificationDto getSentNotification(String iun) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.PN_DELIVERY, "getSentNotificationPrivate");
        var response = internalOnlyApi.getSentNotificationPrivate(iun);
        List<NotificationRecipientDto> recipients = response.getRecipients() == null ? List.of() :
                response.getRecipients().stream()
                        .map(r -> NotificationRecipientDto.builder().internalId(r.getInternalId()).build())
                        .toList();
        return NotificationDto.builder()
                .iun(iun)
                .subject(response.getSubject())
                .senderDenomination(response.getSenderDenomination())
                .recipients(recipients)
                .build();
    }
}
