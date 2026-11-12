package it.pagopa.pn.ioconnector.generated;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationStatus;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedDtoToStringTest {

    private static final String TAX_ID = "RSSMRA80A01H501U";

    @Test
    void fiscalCodePayload_toString_excludesFiscalCode() {
        FiscalCodePayload dto = new FiscalCodePayload().fiscalCode(TAX_ID);

        assertThat(dto.toString()).doesNotContain(TAX_ID);
    }

    @Test
    void activationPayload_toString_excludesFiscalCode() {
        ActivationPayload dto = new ActivationPayload().fiscalCode(TAX_ID).status(ActivationStatus.ACTIVE);

        assertThat(dto.toString()).doesNotContain(TAX_ID).contains("ACTIVE");
    }

    @Test
    void activation_toString_excludesFiscalCode() {
        Activation dto = new Activation().fiscalCode(TAX_ID).status(ActivationStatus.ACTIVE);

        assertThat(dto.toString()).doesNotContain(TAX_ID).contains("ACTIVE");
    }

    @Test
    void userStatusRequest_toString_excludesTaxId() {
        UserStatusRequest dto = new UserStatusRequest().taxId(TAX_ID);

        assertThat(dto.toString()).doesNotContain(TAX_ID);
    }

    @Test
    void userStatusResponse_toString_excludesTaxId() {
        UserStatusResponse dto = new UserStatusResponse().taxId(TAX_ID).status(UserStatusResponse.StatusEnum.PN_ACTIVE);

        assertThat(dto.toString()).doesNotContain(TAX_ID).contains("PN_ACTIVE");
    }

    @Test
    void sendMessageRequest_toString_excludesRecipientTaxId() {
        SendMessageRequest dto = new SendMessageRequest()
                .iun("IUN-001")
                .recipientTaxID(TAX_ID)
                .recipientInternalID("PF-abc123");

        assertThat(dto.toString()).doesNotContain(TAX_ID).contains("IUN-001");
    }

    @Test
    void sendMessageRequest_toString_excludesSubject() {
        SendMessageRequest dto = new SendMessageRequest()
                .iun("IUN-001")
                .recipientTaxID(TAX_ID)
                .subject("Sensitive subject");

        assertThat(dto.toString()).doesNotContain("Sensitive subject").contains("IUN-001");
    }

    @Test
    void messageRequest_toString_excludesSubjectAndMarkdown() {
        MessageRequest dto = new MessageRequest()
                .requestId("REQ-001")
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Sensitive subject")
                .markdown("Sensitive markdown body");

        assertThat(dto.toString())
                .doesNotContain("Sensitive subject")
                .doesNotContain("Sensitive markdown body")
                .contains("REQ-001");
    }
}
