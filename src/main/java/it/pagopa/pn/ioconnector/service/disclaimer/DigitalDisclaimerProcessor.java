package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class DigitalDisclaimerProcessor implements BottomSheetProcessor {

    private static final String TITLE = "Questo messaggio contiene una notifica a valore legale";
    private static final String TEMPLATE_PATH = "templates/markdown_disclaimer_digital_app_io_message.md";

    @Override
    public PreconditionContent process(DeliveryInformationDto deliveryInformation) {
        try {
            String template = new String(new ClassPathResource(TEMPLATE_PATH).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String markdown = template
                    .replace("{{iun}}", deliveryInformation.getIun() != null ? deliveryInformation.getIun() : "")
                    .replace("{{senderDenomination}}", "")
                    .replace("{{subject}}", "");
            return new PreconditionContent(TITLE, markdown);
        } catch (IOException e) {
            throw new it.pagopa.pn.commons.exceptions.PnInternalException(
                    "Error loading disclaimer template: " + TEMPLATE_PATH,
                    "DISCLAIMER_TEMPLATE_ERROR",
                    e
            );
        }
    }
}
