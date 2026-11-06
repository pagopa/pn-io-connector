package it.pagopa.pn.ioconnector.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomePollingRequestTest {

    private static final String TAX_ID = "RSSMRA80A01H501U";

    @Test
    void toString_excludesRecipientTaxId() {
        OutcomePollingRequest request = OutcomePollingRequest.builder()
                .requestId("REQ-001")
                .iun("IUN-001")
                .recipientTaxId(TAX_ID)
                .ioMessageId("IO-MSG-001")
                .build();

        assertThat(request.toString())
                .doesNotContain(TAX_ID)
                .contains("REQ-001");
    }
}
