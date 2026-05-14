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
public class IoOutcomeEvent {

    private String requestId;
    private String cxId;
    private String ioMessageId;
    private OutcomeType outcomeType;
    private String ioStatus;
    private String readStatus;
    private String paymentStatus;
    private boolean pollingExhausted;
    private Instant eventTimestamp;
}
