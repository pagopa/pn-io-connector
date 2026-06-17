package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnIoGetProfileException;

import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.middleware.msclient.IOClient;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.MessageContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Payee;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.PaymentData;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ThirdPartyData;
import it.pagopa.pn.ioconnector.model.EventType;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.model.io.MessageStatusValue;

import java.util.EnumSet;
import java.util.Set;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public class IOService {

    private final IOClient ioClient;
    private final PnIoConnectorConfig pnIoConnectorConfig;

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
            if (e.getStatusCode() == 429 || e.getStatusCode() >= 500) {
                throw e;
            }
            throw new PnIoGetProfileException(e.getStatusCode(), e.getMessage());
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
            thirdPartyData.setConfigurationId(pnIoConnectorConfig.getIoConfigurationId());
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

    /**
     * Restituisce l'insieme degli stati IO raggiunti dal messaggio, derivati indipendentemente dai tre
     * attributi {@code getMessage} ({@code status}, {@code read_status}, {@code payment_status}).
     * Insieme vuoto = nulla di osservabile (o risposta nulla) → il polling prosegue.
     */
    public Set<EventType> getReachedEventTypes(String taxId, String ioMessageId, String apiKey) {
        return mapToReachedEventTypes(ioClient.getMessageStatus(taxId, ioMessageId, apiKey));
    }

    private Set<EventType> mapToReachedEventTypes(ExternalMessageResponseWithContent response) {
        EnumSet<EventType> reached = EnumSet.noneOf(EventType.class);
        if (response == null) {
            return reached;
        }
        MessageStatusValue status = response.getStatus();
        if (status == MessageStatusValue.FAILED || status == MessageStatusValue.REJECTED) {
            reached.add(EventType.IO_DELIVERY_FAILED);
            return reached;
        }
        boolean read = "READ".equals(response.getReadStatus());
        boolean paid = "PAID".equals(response.getPaymentStatus());
        // consegna inferita anche da READ/PAID (presuppongono la consegna; PROCESSED è sticky)
        if (status == MessageStatusValue.PROCESSED || read || paid) {
            reached.add(EventType.DELIVERED_TO_USER);
        }
        if (read) {
            reached.add(EventType.READ);
        }
        if (paid) {
            reached.add(EventType.PAID);
        }
        return reached;
    }

    public String getServiceUseKey(String serviceId) {
        return ioClient.getServiceUseKey(serviceId);
    }
}
