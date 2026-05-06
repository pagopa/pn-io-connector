package it.pagopa.pn.ioconnector.service.io.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IoMessageStatusResponse {

    private String id;

    @JsonProperty("fiscal_code")
    private String fiscalCode;

    @JsonProperty("created_at")
    private String createdAt;

    private String status;

    @JsonProperty("read_status")
    private String readStatus;

    @JsonProperty("payment_status")
    private String paymentStatus;
}
