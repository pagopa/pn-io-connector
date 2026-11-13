package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class AnalogDisclaimerProcessorTest {

    private final AnalogDisclaimerProcessor processor = new AnalogDisclaimerProcessor();
    private static final ZoneId ZONE_ROME = ZoneId.of("Europe/Rome");

    @Test
    void process_WhenSchedulingDatePresent_ThenMarkdownContainsDateAndTime() {
        Instant schedulingDate = Instant.parse("2026-11-15T09:30:00Z");
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .iun("AAAA-BBBB-CCCC-202610-1-1")
                .deliveryMode("ANALOG")
                .schedulingAnalogDate(schedulingDate)
                .build();

        PreconditionContent result = processor.process(dto);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Questo messaggio contiene una notifica a valore legale");
        assertThat(result.getMarkdown()).doesNotContain("{{data}}");
        assertThat(result.getMarkdown()).doesNotContain("{{ora}}");
        assertThat(result.getMarkdown()).doesNotContain("{{iun}}");
        String expectedDate = DateTimeFormatter.ofPattern("dd-MM-yyyy").withZone(ZONE_ROME).format(schedulingDate);
        assertThat(result.getMarkdown()).contains(expectedDate);
    }

    @Test
    void process_WhenSchedulingDateNull_ThenPlaceholdersReplacedWithEmpty() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .iun("AAAA-BBBB-CCCC-202610-1-1")
                .deliveryMode("ANALOG")
                .schedulingAnalogDate(null)
                .build();

        PreconditionContent result = processor.process(dto);

        assertThat(result).isNotNull();
        assertThat(result.getMarkdown()).doesNotContain("{{data}}");
        assertThat(result.getMarkdown()).doesNotContain("{{ora}}");
    }
}
