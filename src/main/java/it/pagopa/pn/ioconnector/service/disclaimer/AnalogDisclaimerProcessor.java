package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class AnalogDisclaimerProcessor implements BottomSheetProcessor {

    private static final String TITLE = "Questo messaggio contiene una notifica a valore legale";
    private static final String TEMPLATE_PATH = "templates/markdown_disclaimer_before_analog_date_app_io_message.md";
    private static final ZoneId ZONE_ROME = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy").withZone(ZONE_ROME);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE_ROME);

    @Override
    public PreconditionContent process(DeliveryInformationDto deliveryInformation) {
        try {
            String template = new String(new ClassPathResource(TEMPLATE_PATH).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String data = deliveryInformation.getSchedulingAnalogDate() != null
                    ? DATE_FORMATTER.format(deliveryInformation.getSchedulingAnalogDate()) : "";
            String ora = deliveryInformation.getSchedulingAnalogDate() != null
                    ? TIME_FORMATTER.format(deliveryInformation.getSchedulingAnalogDate()) : "";
            String markdown = template
                    .replace("{{iun}}", deliveryInformation.getIun() != null ? deliveryInformation.getIun() : "")
                    .replace("{{senderDenomination}}", "")
                    .replace("{{subject}}", "")
                    .replace("{{data}}", data)
                    .replace("{{ora}}", ora);
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
