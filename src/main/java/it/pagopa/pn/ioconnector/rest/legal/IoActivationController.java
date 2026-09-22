package it.pagopa.pn.ioconnector.rest.legal;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.api.IoActivationApi;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.service.legal.IoActivationService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

@CustomLog
@RestController
@RequiredArgsConstructor
public class IoActivationController implements IoActivationApi {

    private final IoActivationService ioActivationService;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<Activation> getIoActivation(FiscalCodePayload fiscalCodePayload) {
        return ResponseEntity.ok(ioActivationService.getActivation(fiscalCodePayload));
    }

    @Override
    public ResponseEntity<Activation> upsertIoActivation(String xPagopaPnUid,
                                                         CxTypeAuthFleet xPagopaPnCxType,
                                                         String xPagopaPnCxId,
                                                         ActivationPayload activationPayload) {
        return ResponseEntity.ok(ioActivationService.upsertActivation(
                xPagopaPnUid, xPagopaPnCxType, xPagopaPnCxId, activationPayload));
    }
}
