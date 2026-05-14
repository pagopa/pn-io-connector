package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.log.PnLogger;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.api.RecipientsApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.dto.BaseRecipientDto;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CustomLog
@Component
@RequiredArgsConstructor
public class DataVaultClient {

    private final RecipientsApi recipientsApi;

    public List<BaseRecipientDto> deanonymize(String internalId) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.PN_DATA_VAULT, "recipientsApi");
        List<String> internalIdParam = Collections.singletonList(internalId);
        return recipientsApi.getRecipientDenominationByInternalId(internalIdParam);
    }
}
