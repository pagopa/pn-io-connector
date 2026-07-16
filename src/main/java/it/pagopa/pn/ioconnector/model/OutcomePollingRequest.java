package it.pagopa.pn.ioconnector.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OutcomePollingRequest {

    private String requestId;
    private String xPagopaIoConCxId;
    private String iun;
    private String recipientTaxId;
    private String ioMessageId;
    private String senderServiceId;
    private boolean paymentData;
    private String noticeCode;
    private EventType lastKnownStatus;
    private Instant pollingMaxDate;
    private long pollingIntervalSeconds;
    private int attemptCount;
    private Long enqueuedAt;
}
