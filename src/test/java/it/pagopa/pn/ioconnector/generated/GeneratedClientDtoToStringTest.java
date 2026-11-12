package it.pagopa.pn.ioconnector.generated;

import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.dto.BaseRecipientDto;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.MessageContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedClientDtoToStringTest {

    private static final String TAX_ID = "RSSMRA80A01H501U";

    @Test
    void ioFiscalCodePayload_toString_excludesFiscalCode() {
        FiscalCodePayload dto = new FiscalCodePayload().fiscalCode(TAX_ID);

        assertThat(dto.toString()).doesNotContain(TAX_ID);
    }

    @Test
    void ioNewMessage_toString_excludesFiscalCode() {
        NewMessage dto = new NewMessage().fiscalCode(TAX_ID);

        assertThat(dto.toString()).doesNotContain(TAX_ID);
    }

    @Test
    void ioMessageContent_toString_excludesSubjectAndMarkdown() {
        MessageContent dto = new MessageContent()
                .subject("Sensitive subject of the message")
                .markdown("Sensitive markdown body of the message");

        assertThat(dto.toString())
                .doesNotContain("Sensitive subject of the message")
                .doesNotContain("Sensitive markdown body of the message");
    }

    @Test
    void ioNewMessage_toString_withContent_excludesSubjectAndMarkdown() {
        NewMessage dto = new NewMessage()
                .fiscalCode(TAX_ID)
                .content(new MessageContent()
                        .subject("Sensitive subject of the message")
                        .markdown("Sensitive markdown body of the message"));

        assertThat(dto.toString())
                .doesNotContain(TAX_ID)
                .doesNotContain("Sensitive subject of the message")
                .doesNotContain("Sensitive markdown body of the message");
    }

    @Test
    void ioActivation_toString_excludesFiscalCode() {
        Activation dto = new Activation().fiscalCode(TAX_ID).status("ACTIVE");

        assertThat(dto.toString()).doesNotContain(TAX_ID).contains("ACTIVE");
    }

    @Test
    void dataVaultBaseRecipientDto_toString_excludesTaxIdAndDenomination() {
        BaseRecipientDto dto = new BaseRecipientDto()
                .internalId("PF-abc123")
                .taxId(TAX_ID)
                .denomination("Mario Rossi");

        assertThat(dto.toString())
                .doesNotContain(TAX_ID)
                .doesNotContain("Mario Rossi")
                .contains("PF-abc123");
    }
}
