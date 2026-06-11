package it.pagopa.pn.ioconnector.service;

import it.pagopa.pn.ioconnector.exceptions.PnDataVaultException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.dto.BaseRecipientDto;
import it.pagopa.pn.ioconnector.middleware.msclient.DataVaultClient;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_DATAVAULT_DEANONYMIZE_ERROR;

@Service
@CustomLog
@RequiredArgsConstructor
public class DataVaultService {

    private final DataVaultClient dataVaultClient;

    public String deanonymize(String internalId) {
        List<BaseRecipientDto> resp = dataVaultClient.deanonymize(internalId);
        if (resp == null || resp.isEmpty()) {
            throw buildDeanonymizeNotFoundException();
        }
        return resp.stream()
                .filter(r -> internalId.equals(r.getInternalId()))
                .map(BaseRecipientDto::getTaxId)
                .findFirst()
                .orElseThrow(this::buildDeanonymizeNotFoundException);
    }

    private PnDataVaultException buildDeanonymizeNotFoundException() {
        return new PnDataVaultException(
                HttpStatus.NOT_FOUND.value(),
                ERROR_CODE_IOCONNECTOR_DATAVAULT_DEANONYMIZE_ERROR
        );
    }
}
