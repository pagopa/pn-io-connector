package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.ioconnector.exceptions.PnNotImplementedException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.FiscalCodePayload;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@CustomLog
@Service
@RequiredArgsConstructor
public class IoActivationService {

    public Activation getActivation(FiscalCodePayload request) {
        throw new PnNotImplementedException("getIoActivation");
    }

    public Activation upsertActivation(String xPagopaPnUid,
                                       CxTypeAuthFleet xPagopaPnCxType,
                                       String xPagopaPnCxId,
                                       ActivationPayload request) {
        throw new PnNotImplementedException("upsertIoActivation");
    }
}
