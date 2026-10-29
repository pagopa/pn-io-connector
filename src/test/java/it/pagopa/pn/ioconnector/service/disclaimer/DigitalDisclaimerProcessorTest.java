package it.pagopa.pn.ioconnector.service.disclaimer;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.model.DeliveryInformationDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DigitalDisclaimerProcessorTest {

    private final DigitalDisclaimerProcessor processor = new DigitalDisclaimerProcessor();

    @Test
    void process_WhenIunPresent_ThenMarkdownContainsIun() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .iun("AAAA-BBBB-CCCC-202610-1-1")
                .deliveryMode("DIGITAL")
                .build();

        PreconditionContent result = processor.process(dto);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Questo messaggio contiene una notifica a valore legale");
        assertThat(result.getMarkdown()).contains("AAAA-BBBB-CCCC-202610-1-1");
        assertThat(result.getMarkdown()).doesNotContain("{{iun}}");
        assertThat(result.getMarkdown()).doesNotContain("{{senderDenomination}}");
    }

    @Test
    void process_WhenIunNull_ThenMarkdownPlaceholderReplaced() {
        DeliveryInformationDto dto = DeliveryInformationDto.builder()
                .iun(null)
                .deliveryMode("DIGITAL")
                .build();

        PreconditionContent result = processor.process(dto);

        assertThat(result).isNotNull();
        assertThat(result.getMarkdown()).doesNotContain("{{iun}}");
    }
}
