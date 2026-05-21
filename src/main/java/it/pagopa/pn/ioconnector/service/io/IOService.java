package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.exceptions.PnIOGetProfileException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.middleware.msclient.IOClient;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.MessageContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Payee;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.PaymentData;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ThirdPartyData;
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
        try {
            return ioClient.checkUserProfile(fiscalCodePayload, apiKey);
        } catch (PnHttpResponseException e) {
            if (e.getStatusCode() == 404) {
                LimitedProfile notFound = new LimitedProfile();
                notFound.setSenderAllowed(false);
                return notFound;
            }
            throw new PnIOGetProfileException(e.getStatusCode(), e.getMessage());
        }
    }

    public String sendMessage(MessageSendRequest request, String apiKey) {
        MessageContent content = new MessageContent();
        content.setSubject(request.getSubject());
        content.setMarkdown(request.getMarkdown());
        content.setDueDate(request.getDueDate());

        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            ThirdPartyData thirdPartyData = new ThirdPartyData();
            thirdPartyData.setId(request.getRequestId());
            thirdPartyData.setHasAttachments(true);
            content.setThirdPartyData(thirdPartyData);
        }

        if (request.getPaymentData() != null) {
            MessageSendRequest.PaymentData pd = request.getPaymentData();
            Payee payee = new Payee();
            payee.setFiscalCode(pd.getCreditorTaxId());
            PaymentData paymentData = new PaymentData();
            paymentData.setAmount(pd.getAmount());
            paymentData.setNoticeNumber(pd.getNoticeCode());
            paymentData.setInvalidAfterDueDate(pd.getInvalidAfterDueDate());
            paymentData.setPayee(payee);
            content.setPaymentData(paymentData);
        }

        NewMessage newMessage = new NewMessage();
        newMessage.setFiscalCode(request.getRecipientTaxId());
        newMessage.setContent(content);
        newMessage.setFeatureLevelType("ADVANCED");

        return ioClient.sendMessage(newMessage, apiKey);
    }

    public OutcomeEvent getMessageStatus(String taxId, String ioMessageId, String apiKey) {
        // TODO: mappare ExternalMessageResponseWithContent → OutcomeEvent
        ioClient.getMessageStatus(taxId, ioMessageId, apiKey);
        return null;
    }

    public String getServiceUseKey(String serviceId) {
        return ioClient.getServiceUseKey(serviceId);
    }
}
