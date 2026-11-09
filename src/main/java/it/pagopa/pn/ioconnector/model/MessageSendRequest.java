package it.pagopa.pn.ioconnector.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {

    private String requestId;
    private String xPagopaIoConCxId;
    private String iun;
    private String recipientTaxId;
    private String senderServiceId;
    @ToString.Exclude
    private String subject;
    @ToString.Exclude
    private String markdown;
    private List<Attachment> attachments;
    private Boolean sensitiveContent;
    private String dueDate;
    private PaymentData paymentData;
    private Instant pollingMaxDate;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String id;
        private String name;
        private String fileKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentData {
        private Integer amount;
        private String noticeCode;
        private String creditorTaxId;
        private Boolean invalidAfterDueDate;
    }
}
