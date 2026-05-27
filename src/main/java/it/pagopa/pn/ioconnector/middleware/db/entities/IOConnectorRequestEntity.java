package it.pagopa.pn.ioconnector.middleware.db.entities;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;

@DynamoDbBean
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IOConnectorRequestEntity {

    public static final String COL_REQUEST_ID = "requestId";
    public static final String COL_IO_MESSAGE_ID = "ioMessageId";
    public static final String GSI_IO_MESSAGE_ID_INDEX = "ioMessageIdIndex";

    @Getter(onMethod = @__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_REQUEST_ID)}))
    private String requestId;

    @Getter(onMethod = @__({@DynamoDbSecondaryPartitionKey(indexNames = {GSI_IO_MESSAGE_ID_INDEX}), @DynamoDbAttribute(COL_IO_MESSAGE_ID)}))
    private String ioMessageId;

    private String iun;
    private String xPagopaIoConCxId;
    private String recipientTaxId;
    private String senderServiceId;
    private String subject;
    private String markdown;
    private List<Attachment> attachments;
    private Boolean sensitiveContent;
    private PaymentData paymentData;
    private List<Event> eventList;
    private String status;
    private String pollingMaxDate;
    private String createdAt;
    private String updatedAt;
    private Long ttl;
    private Integer retryStep;
    private String lastRetryTimestamp;

    @DynamoDbBean
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String id;
        private String fileKey;
        private String name;
    }

    @DynamoDbBean
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentData {
        private Integer amount;
        private String noticeCode;
        private String creditorTaxId;
        private String payee;
        private Boolean invalidAfterDueDate;
    }

    @DynamoDbBean
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        private String eventDate;
        private String status;
    }
}
