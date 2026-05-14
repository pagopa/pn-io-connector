package it.pagopa.pn.ioconnector.service;

import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.dto.BaseRecipientDto;
import it.pagopa.pn.ioconnector.middleware.msclient.DataVaultClient;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@CustomLog
@RequiredArgsConstructor
public class DataVaultService {

    private final DataVaultClient dataVaultClient;

    public String deanonymize(String internalId) {
        List<BaseRecipientDto> resp = dataVaultClient.deanonymize(internalId);
        return resp.stream()
                .filter(r -> internalId.equals(r.getInternalId()))
                .map(BaseRecipientDto::getTaxId)
                .findFirst()
                .orElseThrow();
    }
}
