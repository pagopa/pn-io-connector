package it.pagopa.pn.ioconnector.middleware.db;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

import java.time.Instant;
import java.util.Optional;

@Repository
@Slf4j
public class IOConnectorRequestDaoImpl implements IOConnectorRequestDao {

    private final DynamoDbTable<IOConnectorRequestEntity> requestsTable;

    public IOConnectorRequestDaoImpl(DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                     PnIoConnectorConfig config) {
        this.requestsTable = dynamoDbEnhancedClient.table(
                config.getDynamodbTableName(),
                TableSchema.fromBean(IOConnectorRequestEntity.class));
    }

    @Override
    public void save(IOConnectorRequestEntity entity) {
        log.debug("save requestId={}", entity.getRequestId());
        requestsTable.putItem(entity);
    }

    @Override
    public Optional<IOConnectorRequestEntity> findById(String requestId) {
        log.debug("findById requestId={}", requestId);
        Key key = Key.builder().partitionValue(requestId).build();
        return Optional.ofNullable(requestsTable.getItem(key));
    }

    @Override
    public Optional<IOConnectorRequestEntity> findByIdConsistentRead(String requestId) {
        log.debug("findByIdConsistentRead requestId={}", requestId);
        Key key = Key.builder().partitionValue(requestId).build();
        return Optional.ofNullable(requestsTable.getItem(r -> r.key(key).consistentRead(true)));
    }

    @Override
    public Optional<IOConnectorRequestEntity> findByIoMessageId(String ioMessageId) {
        log.debug("findByIoMessageId ioMessageId={}", ioMessageId);
        DynamoDbIndex<IOConnectorRequestEntity> index =
                requestsTable.index(IOConnectorRequestEntity.GSI_IO_MESSAGE_ID_INDEX);
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(ioMessageId));
        return index.query(q -> q.queryConditional(condition))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    @Override
    public IOConnectorRequestEntity update(IOConnectorRequestEntity entity) {
        log.debug("update requestId={}", entity.getRequestId());
        entity.setUpdatedAt(Instant.now().toString());
        UpdateItemEnhancedRequest<IOConnectorRequestEntity> request =
                UpdateItemEnhancedRequest.builder(IOConnectorRequestEntity.class)
                        .item(entity)
                        .ignoreNulls(true)
                        .build();
        return requestsTable.updateItem(request);
    }
}
