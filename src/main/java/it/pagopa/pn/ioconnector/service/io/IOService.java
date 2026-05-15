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
            if (e.getStatusCode() == 404) {
                LimitedProfile notFound = new LimitedProfile();
                notFound.setSenderAllowed(false);
                return notFound;
            }
            throw new PnIOGetProfileException(e.getStatusCode(), e.getMessage());
        }
        return limitedProfile;
    }

    public String sendMessage(MessageSendRequest request, String apiKey) {
        var newMessage = new NewMessage()
                .fiscalCode(request.getRecipientTaxId())
                .featureLevelType("ADVANCED")
                .content(buildMessageContent(request));
        return ioClient.sendMessage(newMessage, apiKey);
    }

    private MessageContent buildMessageContent(MessageSendRequest request) {
        return new MessageContent()
                .subject(request.getSubject())
                .markdown(request.getMarkdown())
                .dueDate(request.getDueDate() != null ? request.getDueDate() : "2026-06-30T23:59:59Z")
                .paymentData(buildPaymentData(request.getPaymentData()));
    }

    private PaymentData buildPaymentData(MessageSendRequest.PaymentData pd) {
        if (pd == null) return null;
        return new PaymentData()
                .amount(pd.getAmount())
                .noticeNumber(pd.getNoticeCode())
                .invalidAfterDueDate(pd.getInvalidAfterDueDate() != null ? pd.getInvalidAfterDueDate() : true)
                .payee(new Payee().fiscalCode(pd.getCreditorTaxId()));
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
