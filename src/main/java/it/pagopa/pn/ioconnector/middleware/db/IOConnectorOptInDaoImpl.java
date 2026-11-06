package it.pagopa.pn.ioconnector.middleware.db;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorOptInEntity;
import lombok.CustomLog;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
@CustomLog
public class IOConnectorOptInDaoImpl implements IOConnectorOptInDao {

    private final DynamoDbTable<IOConnectorOptInEntity> optInTable;

    public IOConnectorOptInDaoImpl(DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                   PnIoConnectorConfig config) {
        this.optInTable = dynamoDbEnhancedClient.table(
                config.getOptinDynamodbTableName(),
                TableSchema.fromBean(IOConnectorOptInEntity.class));
    }

    @Override
    public void save(IOConnectorOptInEntity entity) {
        optInTable.updateItem(entity);
    }

    @Override
    public Optional<IOConnectorOptInEntity> get(String hashedTaxId) {
        Key key = Key.builder().partitionValue(hashedTaxId).build();
        return Optional.ofNullable(optInTable.getItem(key));
    }
}
