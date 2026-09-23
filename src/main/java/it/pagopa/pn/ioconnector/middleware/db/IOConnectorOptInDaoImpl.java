package it.pagopa.pn.ioconnector.middleware.db;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorOptInEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
@Slf4j
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
        log.debug("save pk={}", entity.getPk());
        optInTable.updateItem(entity);
    }

    @Override
    public Optional<IOConnectorOptInEntity> get(String hashedTaxId) {
        log.debug("get pk={}", hashedTaxId);
        Key key = Key.builder().partitionValue(hashedTaxId).build();
        return Optional.ofNullable(optInTable.getItem(key));
    }
}
