package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.middleware.msclient.TimelineClient;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@CustomLog
@Service
@RequiredArgsConstructor
public class NotificationDisclaimerService {

    private final TimelineClient timelineClient;
    private final BottomSheetProcessorFactory bottomSheetProcessorFactory;

    public PreconditionContent getNotificationDisclaimer(String iun, String cxId) {
        DeliveryInformationDto deliveryInfo = timelineClient.getDeliveryInformation(iun, 0);
        deliveryInfo.setIun(iun);
        if ("UNKNOWN".equals(deliveryInfo.getDeliveryMode())) {
            throw new PnHttpResponseException("Delivery mode is UNKNOWN for iun " + iun, 404);
        }
        BottomSheetProcessor processor = bottomSheetProcessorFactory.getProcessor(deliveryInfo);
        return processor.process(deliveryInfo);
    }
}
