package it.pagopa.pn.ioconnector.middleware.db.entities;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IOConnectorRequestEntityTest {

    @Test
    void toString_excludesSubjectAndMarkdown() {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .iun("IUN-001")
                .subject("Sensitive subject")
                .markdown("Sensitive markdown body")
                .build();

        assertThat(entity.toString())
                .doesNotContain("Sensitive subject")
                .doesNotContain("Sensitive markdown body")
                .contains("REQ-001");
    }
}
