package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.exceptions.PnIOGetProfileException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.middleware.msclient.IOClient;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public class IOService {

    private final IOClient ioClient;

    public LimitedProfile checkUserProfile(String taxId, String apiKey) {
        FiscalCodePayload fiscalCodePayload = new FiscalCodePayload();
        fiscalCodePayload.setFiscalCode(taxId);
        LimitedProfile limitedProfile = new LimitedProfile();
        try {
            limitedProfile = ioClient.checkUserProfile(fiscalCodePayload, apiKey);
        } catch (PnHttpResponseException e) {
            throw new PnIOGetProfileException(e.getStatusCode(), e.getMessage());
        }
        return limitedProfile;
    }

    public String sendMessage(MessageSendRequest request, String apiKey) {
        // TODO: mappare MessageSendRequest → NewMessage
        ioClient.sendMessage(new NewMessage(), apiKey);
        return null;
    }

    public OutcomeEvent getMessageStatus(String taxId, String ioMessageId, String apiKey) {
        // TODO: mappare ExternalMessageResponseWithContent → OutcomeEvent
        ioClient.getMessageStatus(taxId, ioMessageId, apiKey);
        return null;
    }

    public String getServiceUseKey(String senderTaxId, String serviceId) {
        // TODO: recuperare la ApiKey MANAGE a partire dal senderTaxId
        ioClient.getServiceUseKey(serviceId, null);
        return "";
    }
}
