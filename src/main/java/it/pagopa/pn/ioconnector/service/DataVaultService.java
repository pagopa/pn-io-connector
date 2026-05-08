package it.pagopa.pn.ioconnector.service;

import it.pagopa.pn.ioconnector.middleware.msclient.DataVaultClient;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public class DataVaultService {

    private final DataVaultClient dataVaultClient;

    public String deanonymize(String internalId) {
        // TODO: invocare il client
        return internalId;
    }
}
