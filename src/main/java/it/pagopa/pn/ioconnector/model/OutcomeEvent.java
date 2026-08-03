package it.pagopa.pn.ioconnector.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomeEvent {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("xPagopaIoConCxId")
    private String xPagopaIoConCxId;

    @JsonProperty("ioMessageId")
    private String ioMessageId;

    @JsonProperty("noticeCode")
    private String noticeCode;

    @JsonProperty("eventType")
    private EventType eventType;

    @JsonProperty("errorDetail")
    private String errorDetail;

    @JsonProperty("eventTimestamp")
    private Instant eventTimestamp;
}
