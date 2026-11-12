package it.pagopa.pn.ioconnector.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSendRequestTest {

    @Test
    void toString_excludesSubjectAndMarkdown() {
        MessageSendRequest request = MessageSendRequest.builder()
                .requestId("REQ-001")
                .iun("IUN-001")
                .subject("Sensitive subject")
                .markdown("Sensitive markdown body")
                .build();

        assertThat(request.toString())
                .doesNotContain("Sensitive subject")
                .doesNotContain("Sensitive markdown body")
                .contains("REQ-001");
    }

    @Test
    void toString_excludesRecipientTaxId() {
        MessageSendRequest request = MessageSendRequest.builder()
                .requestId("REQ-001")
                .recipientTaxId("RSSMRA80A01H501U")
                .build();

        assertThat(request.toString())
                .doesNotContain("RSSMRA80A01H501U")
                .contains("REQ-001");
    }
}
