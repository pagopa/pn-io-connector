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
public class OutcomeEvent {

    private String requestId;
    private String xPagopaIoConCxId;
    private String ioMessageId;
    private EventType eventType;
    private Instant eventTimestamp;
}
