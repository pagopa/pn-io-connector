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
public class IoServiceKeysResponse {

    @JsonProperty("primary_key")
    private String primaryKey;

    @JsonProperty("secondary_key")
    private String secondaryKey;
}
