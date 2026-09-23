package it.pagopa.pn.ioconnector.middleware.db;

import it.pagopa.pn.ioconnector.localstack.LocalStackTestConfig;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorOptInEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(LocalStackTestConfig.class)
class IOConnectorOptInDaoTest {

    private static final int COOLDOWN_DAYS = 30;

    @Autowired
    private IOConnectorOptInDao dao;

    @Test
    void save_WhenNewRecord_ThenPersistsPkCreatedLastModifiedAndTtl() {
        IOConnectorOptInEntity entity = IOConnectorOptInEntity.of("hash-save-001", COOLDOWN_DAYS);

        dao.save(entity);

        Optional<IOConnectorOptInEntity> result = dao.get("hash-save-001");
        assertThat(result).isPresent();
        assertThat(result.get().getPk()).isEqualTo("hash-save-001");
        assertThat(result.get().getCreated()).isEqualTo(entity.getCreated());
        assertThat(result.get().getLastModified()).isEqualTo(entity.getLastModified());
        assertThat(result.get().getTtl()).isEqualTo(entity.getTtl());
    }

    @Test
    void save_WhenRecordAlreadyExists_ThenCreatedIsPreservedAndLastModifiedUpdated() {
        IOConnectorOptInEntity first = IOConnectorOptInEntity.of("hash-upsert-001", COOLDOWN_DAYS);
        dao.save(first);

        IOConnectorOptInEntity second = IOConnectorOptInEntity.of("hash-upsert-001", COOLDOWN_DAYS);
        dao.save(second);

        Optional<IOConnectorOptInEntity> result = dao.get("hash-upsert-001");
        assertThat(result).isPresent();
        assertThat(result.get().getCreated()).isEqualTo(first.getCreated());
        assertThat(result.get().getLastModified()).isEqualTo(second.getLastModified());
        assertThat(result.get().getTtl()).isEqualTo(second.getTtl());
    }

    @Test
    void get_WhenRecordMissing_ThenReturnsEmpty() {
        Optional<IOConnectorOptInEntity> result = dao.get("hash-not-exists");

        assertThat(result).isEmpty();
    }

    @Test
    void of_WhenCooldownDaysConfigured_ThenTtlEqualsLastModifiedPlusDays() {
        IOConnectorOptInEntity entity = IOConnectorOptInEntity.of("hash-ttl-001", COOLDOWN_DAYS);

        long expectedTtl = entity.getLastModified().plus(COOLDOWN_DAYS, ChronoUnit.DAYS).getEpochSecond();
        assertThat(entity.getTtl()).isEqualTo(expectedTtl);
    }

    @Test
    void of_WhenCooldownDaysIsZero_ThenTtlIsAlreadyExpired() {
        IOConnectorOptInEntity entity = IOConnectorOptInEntity.of("hash-ttl-002", 0);

        assertThat(entity.getTtl()).isLessThanOrEqualTo(Instant.now().getEpochSecond());
    }
}
