package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.ioconnector.config.IoLegalMessageTemplates;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.MessageContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ThirdPartyData;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@CustomLog
@Component
@RequiredArgsConstructor
public class IoLegalMessageBuilder {

    static final String FEATURE_LEVEL_TYPE_ADVANCED = "ADVANCED";
    static final int MAX_SUBJECT_LENGTH = 120;
    static final String SENDER_DENOMINATION_PLACEHOLDER = "{{senderDenomination}}";
    static final String URL_CITTADINI_PLACEHOLDER = "${piattaformaNotificheURLCittadini}";
    static final String URL_TOS_PLACEHOLDER = "${piattaformaNotificheURLTOS}";
    static final String URL_PRIVACY_PLACEHOLDER = "${piattaformaNotificheURLPrivacy}";

    private final PnIoConnectorConfig config;
    private final IoLegalMessageTemplates templates;

    public NewMessage buildCourtesyMessage(SendMessageRequest request, boolean localeIsIT) {
        String subject = templates.getSubjectCourtesy()
                .replace(SENDER_DENOMINATION_PLACEHOLDER, request.getSenderDenomination());
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            subject = subject.substring(0, MAX_SUBJECT_LENGTH);
        }

        MessageContent content = new MessageContent()
                .subject(subject)
                .markdown(localeIsIT ? templates.getMarkdownCourtesyIt() : templates.getMarkdownCourtesyEn())
                .dueDate(formatDate(request.getDueDate()))
                .thirdPartyData(new ThirdPartyData()
                        .id(request.getIun())
                        .originalSender(request.getSenderDenomination())
                        .originalReceiptDate(formatDate(request.getRequestAcceptedDate()))
                        .summary(request.getSubject())
                        .configurationId(config.getIoRemoteContentCfgId()));

        return new NewMessage()
                .featureLevelType(FEATURE_LEVEL_TYPE_ADVANCED)
                .fiscalCode(request.getRecipientTaxID())
                .content(content);
    }

    public NewMessage buildOptInMessage(SendMessageRequest request) {
        String markdown;
        if (request.getDeliveryMode() == null || request.getDeliveryMode() == SendMessageRequest.DeliveryModeEnum.ANALOG) {
            log.info("Setting content for IO optIn message using ANALOG template");
            markdown = templates.getMarkdownOptInAnalog();
        } else {
            log.info("Setting content for IO optIn message using DIGITAL template");
            markdown = templates.getMarkdownOptInDigital();
        }

        MessageContent content = new MessageContent()
                .subject(templates.getSubjectOptIn())
                .markdown(composeFinalMarkdown(markdown));

        return new NewMessage()
                .featureLevelType(FEATURE_LEVEL_TYPE_ADVANCED)
                .fiscalCode(request.getRecipientTaxID())
                .content(content);
    }

    private String composeFinalMarkdown(String markdown) {
        return markdown
                .replace(URL_CITTADINI_PLACEHOLDER, config.getPiattaformanotificheurlCittadini())
                .replace(URL_TOS_PLACEHOLDER, config.getPiattaformanotificheurlTos())
                .replace(URL_PRIVACY_PLACEHOLDER, config.getPiattaformanotificheurlPrivacy());
    }


    static String formatDate(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
