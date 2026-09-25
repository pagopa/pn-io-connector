package it.pagopa.pn.ioconnector.middleware.db;

import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorOptInEntity;

import java.util.Optional;

public interface IOConnectorOptInDao {

    void save(IOConnectorOptInEntity entity);

    Optional<IOConnectorOptInEntity> get(String hashedTaxId);
}
