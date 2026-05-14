package it.pagopa.pn.ioconnector.middleware.db;

import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;

import java.util.Optional;

public interface IOConnectorRequestDao {

    void save(IOConnectorRequestEntity entity);

    Optional<IOConnectorRequestEntity> findById(String requestId);

    Optional<IOConnectorRequestEntity> findByIoMessageId(String ioMessageId);

    IOConnectorRequestEntity update(IOConnectorRequestEntity entity);
}
