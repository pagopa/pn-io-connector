package it.pagopa.pn.ioconnector.middleware.db;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

@Repository
@Slf4j
public class IOConnectorRequestDao {

    private final DynamoDbAsyncTable<IOConnectorRequestEntity> requestsTable;

    public IOConnectorRequestDao(DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient,
                                 PnIoConnectorConfig config) {
        this.requestsTable = dynamoDbEnhancedAsyncClient.table(
                config.getDynamodbTableName(),
                TableSchema.fromBean(IOConnectorRequestEntity.class));
    }

    public Mono<Void> save(IOConnectorRequestEntity entity) {
        log.debug("save requestId={}", entity.getRequestId());
        return Mono.fromFuture(requestsTable.putItem(entity));
    }

    public Mono<IOConnectorRequestEntity> findById(String requestId) {
        log.debug("findById requestId={}", requestId);
        Key key = Key.builder().partitionValue(requestId).build();
        return Mono.fromFuture(requestsTable.getItem(key));
    }

    public Mono<IOConnectorRequestEntity> findByIoMessageId(String ioMessageId) {
        log.debug("findByIoMessageId ioMessageId={}", ioMessageId);
        DynamoDbAsyncIndex<IOConnectorRequestEntity> index =
                requestsTable.index(IOConnectorRequestEntity.GSI_IO_MESSAGE_ID_INDEX);
        QueryConditional condition = QueryConditional.keyEqualTo(k -> k.partitionValue(ioMessageId));
        return Flux.from(index.query(q -> q.queryConditional(condition)))
                .flatMap(page -> Flux.fromIterable(page.items()))
                .next();
    }

    public Mono<IOConnectorRequestEntity> update(IOConnectorRequestEntity entity) {
        log.debug("update requestId={}", entity.getRequestId());
        UpdateItemEnhancedRequest<IOConnectorRequestEntity> request =
                UpdateItemEnhancedRequest.builder(IOConnectorRequestEntity.class)
                        .item(entity)
                        .ignoreNulls(true)
                        .build();
        return Mono.fromFuture(requestsTable.updateItem(request));
    }
}
