package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;

public interface BottomSheetProcessor {

    PreconditionContent process(DeliveryInformationDto deliveryInformation);
}
