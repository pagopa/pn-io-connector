package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.middleware.msclient.IOClient;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.MessageContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Payee;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.PaymentData;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ThirdPartyData;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.OutcomeEvent;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;

import java.time.Instant;
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
            throw e;
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

    public OutcomeEvent getMessageStatus(String requestId, String xPagopaIoConCxId,
                                         String taxId, String ioMessageId, String apiKey) {
        ExternalMessageResponseWithContent response = ioClient.getMessageStatus(taxId, ioMessageId, apiKey);
        if (response == null) {
            return null;
        }
        return OutcomeEvent.builder()
                .requestId(requestId)
                .xPagopaIoConCxId(xPagopaIoConCxId)
                .ioMessageId(ioMessageId)
                .eventType(mapToEventType(response))
                .eventTimestamp(Instant.now())
                .build();
    }

    private EventType mapToEventType(ExternalMessageResponseWithContent response) {
        if ("PAID".equals(response.getPaymentStatus())) {
            return EventType.PAID;
        }
        if ("READ".equals(response.getReadStatus())) {
            return EventType.READ;
        }
        return EventType.DELIVERED_TO_USER;
    }

    public String getServiceUseKey(String serviceId) {
        return ioClient.getServiceUseKey(serviceId);
    }
}
