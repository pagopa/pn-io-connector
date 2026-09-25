package it.pagopa.pn.ioconnector.middleware.db.entities;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.UpdateBehavior;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@DynamoDbBean
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IOConnectorOptInEntity {

    public static final String COL_PK = "pk";
    public static final String COL_CREATED = "created";
    public static final String COL_LAST_MODIFIED = "lastModified";
    public static final String COL_I_TTL = "i_ttl";

    public static IOConnectorOptInEntity of(String hashedTaxId, int cooldownDays) {
        Instant now = Instant.now();
        return IOConnectorOptInEntity.builder()
                .pk(hashedTaxId)
                .created(now)
                .lastModified(now)
                .ttl(now.plus(cooldownDays, ChronoUnit.DAYS).getEpochSecond())
                .build();
    }

    @Getter(onMethod = @__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_PK)}))
    private String pk;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_CREATED),
            @DynamoDbUpdateBehavior(UpdateBehavior.WRITE_IF_NOT_EXISTS)}))
    private Instant created;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_LAST_MODIFIED)}))
    private Instant lastModified;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_I_TTL)}))
    private Long ttl;
}
