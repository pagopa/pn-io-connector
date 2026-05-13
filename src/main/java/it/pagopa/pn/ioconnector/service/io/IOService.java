package it.pagopa.pn.ioconnector.service.io;

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

    public boolean checkUserProfile(String taxId, String apiKey) {
        // TODO: mappare fiscal code in payload
        return ioClient.checkUserProfile(new FiscalCodePayload(), apiKey);
    }

    public String sendMessage(MessageSendRequest request, String apiKey) {
        // TODO: mappare MessageSendRequest → NewMessage
        ioClient.sendMessage(new NewMessage(), apiKey);
        return null;
    }

    public OutcomeEvent getMessageStatus(String taxId, String ioMessageId, String apiKey) {
        // TODO: mappare ExternalMessageResponseWithContent → IoOutcomeEvent
        ioClient.getMessageStatus(taxId, ioMessageId, apiKey);
        return null;
    }

    public String getServiceUseKey(String senderTaxId, String serviceId) {
        // TODO: recuperare la ApiKey MANAGE a partire dal senderTaxId
        ioClient.getServiceUseKey(serviceId, null);
        return "";
    }
}
