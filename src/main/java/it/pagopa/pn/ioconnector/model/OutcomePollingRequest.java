package it.pagopa.pn.ioconnector.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomePollingRequest {

    private String requestId;
    private String xPagopaIoConCxId;
    private String iun;
    private String recipientTaxId;
    private String ioMessageId;
    private boolean paymentData;
    private EventType lastKnownStatus;
    private Instant pollingMaxDate;
    private Instant nextPollAfter;
    private int attemptCount;
}
