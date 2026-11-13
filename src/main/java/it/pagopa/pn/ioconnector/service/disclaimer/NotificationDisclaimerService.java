package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.middleware.msclient.DeliveryClient;
import it.pagopa.pn.ioconnector.middleware.msclient.TimelineClient;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import it.pagopa.pn.ioconnector.model.NotificationDto;
import it.pagopa.pn.ioconnector.model.NotificationRecipientDto;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@CustomLog
@Service
@RequiredArgsConstructor
public class NotificationDisclaimerService {

    private final TimelineClient timelineClient;
    private final DeliveryClient deliveryClient;
    private final BottomSheetProcessorFactory bottomSheetProcessorFactory;
    private final PnIoConnectorConfig cfg;

    public PreconditionContent getNotificationDisclaimer(String iun, String cxId) {
        NotificationDto notification = deliveryClient.getSentNotification(iun);
        int recIndex = resolveRecipientIndex(cxId, iun, notification);

        DeliveryInformationDto deliveryInfo = timelineClient.getDeliveryInformation(iun, recIndex);
        deliveryInfo.setIun(iun);
        deliveryInfo.setSenderDenomination(notification.getSenderDenomination());
        deliveryInfo.setSubject(notification.getSubject());

        if ("UNKNOWN".equals(deliveryInfo.getDeliveryMode())) {
            throw new PnHttpResponseException("Delivery mode is UNKNOWN for iun " + iun, 404);
        }
        BottomSheetProcessor processor = bottomSheetProcessorFactory.getProcessor(deliveryInfo);
        PreconditionContent content = processor.process(deliveryInfo);
        return resolveUrls(content);
    }

    private PreconditionContent resolveUrls(PreconditionContent content) {
        String markdown = content.getMarkdown()
                .replace("${piattaformaNotificheURLPrivacy}", cfg.getPiattaformanotificheurlPrivacy() != null ? cfg.getPiattaformanotificheurlPrivacy() : "")
                .replace("${piattaformaNotificheURLTOS}", cfg.getPiattaformanotificheurlTos() != null ? cfg.getPiattaformanotificheurlTos() : "");
        return content.markdown(markdown);
    }

    private int resolveRecipientIndex(String cxId, String iun, NotificationDto notification) {
        List<NotificationRecipientDto> recipients = notification.getRecipients() != null ? notification.getRecipients() : List.of();
        return IntStream.range(0, recipients.size())
                .filter(i -> cxId.equals(recipients.get(i).getInternalId()))
                .findFirst()
                .orElseThrow(() -> new PnHttpResponseException(
                        PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_NOTIFICATION_RECIPIENT_NOT_FOUND,
                        404));
    }
}
