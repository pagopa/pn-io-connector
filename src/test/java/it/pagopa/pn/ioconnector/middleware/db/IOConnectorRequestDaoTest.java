package it.pagopa.pn.ioconnector.middleware.db;

import it.pagopa.pn.ioconnector.localstack.LocalStackTestConfig;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(LocalStackTestConfig.class)
class IOConnectorRequestDaoTest {

    @Autowired
    private IOConnectorRequestDao dao;

    private IOConnectorRequestEntity buildEntity(String requestId) {
        return IOConnectorRequestEntity.builder()
                .requestId(requestId)
                .iun("IUN-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .senderServiceId("SVC-001")
                .subject("Test subject")
                .markdown("Test body")
                .status("ACCEPTED")
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();
    }

    @Test
    void save_WhenValidEntity_ThenPersistsSuccessfully() {
        IOConnectorRequestEntity entity = buildEntity("REQ-SAVE-001");

        dao.save(entity);

        Optional<IOConnectorRequestEntity> result = dao.findById("REQ-SAVE-001");
        assertThat(result).isPresent();
        assertThat(result.get().getRequestId()).isEqualTo("REQ-SAVE-001");
    }

    @Test
    void findById_WhenEntityExists_ThenReturnsIt() {
        IOConnectorRequestEntity entity = buildEntity("REQ-FIND-001");
        dao.save(entity);

        Optional<IOConnectorRequestEntity> result = dao.findById("REQ-FIND-001");

        assertThat(result).isPresent();
        assertThat(result.get().getRequestId()).isEqualTo("REQ-FIND-001");
        assertThat(result.get().getIun()).isEqualTo("IUN-001");
        assertThat(result.get().getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void findById_WhenEntityNotExists_ThenReturnsEmpty() {
        Optional<IOConnectorRequestEntity> result = dao.findById("NOT-EXISTS");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIoMessageId_WhenEntityExists_ThenReturnsViaGsi() {
        IOConnectorRequestEntity entity = buildEntity("REQ-GSI-001");
        entity.setIoMessageId("IO-MSG-001");
        dao.save(entity);

        Optional<IOConnectorRequestEntity> result = dao.findByIoMessageId("IO-MSG-001");

        assertThat(result).isPresent();
        assertThat(result.get().getRequestId()).isEqualTo("REQ-GSI-001");
        assertThat(result.get().getIoMessageId()).isEqualTo("IO-MSG-001");
    }

    @Test
    void findByIoMessageId_WhenNotExists_ThenReturnsEmpty() {
        Optional<IOConnectorRequestEntity> result = dao.findByIoMessageId("IO-MSG-NOT-EXISTS");

        assertThat(result).isEmpty();
    }

    @Test
    void update_WhenEntityExists_ThenUpdatesFields() {
        IOConnectorRequestEntity entity = buildEntity("REQ-UPD-001");
        dao.save(entity);

        entity.setStatus("SENT_TO_IO");
        entity.setIoMessageId("IO-MSG-UPD-001");
        IOConnectorRequestEntity updated = dao.update(entity);

        assertThat(updated.getStatus()).isEqualTo("SENT_TO_IO");
        assertThat(updated.getIoMessageId()).isEqualTo("IO-MSG-UPD-001");
    }

    @Test
    void save_WhenIoMessageIdIsNull_ThenSavedEntityHasNullIoMessageId() {
        IOConnectorRequestEntity entity = buildEntity("REQ-NULL-IO-001");

        dao.save(entity);

        Optional<IOConnectorRequestEntity> result = dao.findById("REQ-NULL-IO-001");
        assertThat(result).isPresent();
        assertThat(result.get().getIoMessageId()).isNull();
        assertThat(result.get().getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void update_WhenCalled_ThenUpdatedAtIsRefreshedOnPersistedRecord() throws InterruptedException {
        IOConnectorRequestEntity entity = buildEntity("REQ-UPD-TS-001");
        String originalUpdatedAt = entity.getUpdatedAt();
        dao.save(entity);

        Thread.sleep(10);

        IOConnectorRequestEntity partialUpdate = IOConnectorRequestEntity.builder()
                .requestId("REQ-UPD-TS-001")
                .status("SENT_TO_IO")
                .build();
        dao.update(partialUpdate);

        Optional<IOConnectorRequestEntity> fromDb = dao.findById("REQ-UPD-TS-001");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getUpdatedAt()).isNotEqualTo(originalUpdatedAt);
    }

    @Test
    void update_WhenIgnoreNulls_ThenOriginalFieldsNotOverwritten() {
        IOConnectorRequestEntity entity = buildEntity("REQ-ISNULL-001");
        dao.save(entity);

        IOConnectorRequestEntity partialUpdate = IOConnectorRequestEntity.builder()
                .requestId("REQ-ISNULL-001")
                .status("SENDER_NOT_ALLOWED")
                .build();
        dao.update(partialUpdate);

        Optional<IOConnectorRequestEntity> fromDb = dao.findById("REQ-ISNULL-001");
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getStatus()).isEqualTo("SENDER_NOT_ALLOWED");
        assertThat(fromDb.get().getIun()).isEqualTo("IUN-001");
        assertThat(fromDb.get().getSubject()).isEqualTo("Test subject");
        assertThat(fromDb.get().getSenderServiceId()).isEqualTo("SVC-001");
        assertThat(fromDb.get().getIoMessageId()).isNull();
    }
}
