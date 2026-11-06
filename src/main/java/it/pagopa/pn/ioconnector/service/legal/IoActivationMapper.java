package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IoActivationMapper {

    public static FiscalCodePayload toIoFiscalCodePayload(String fiscalCode) {
        return new FiscalCodePayload().fiscalCode(fiscalCode);
    }

    public static it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ActivationPayload toIoActivationPayload(ActivationPayload request) {
        return new it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ActivationPayload()
                .fiscalCode(request.getFiscalCode())
                .status(request.getStatus().getValue());
    }

    public static Activation toActivation(it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Activation ioActivation) {
        return new Activation()
                .fiscalCode(ioActivation.getFiscalCode())
                .status(ActivationStatus.fromValue(ioActivation.getStatus()))
                .version(ioActivation.getVersion());
    }

    public static CxTypeAuthFleet toUserAttributesCxType(it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet cxType) {
        return CxTypeAuthFleet.fromValue(cxType.getValue());
    }
}
