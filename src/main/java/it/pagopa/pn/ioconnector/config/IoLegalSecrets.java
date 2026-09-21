package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IoLegalSecrets(
        @JsonProperty("IoApiKey") String ioApiKey,
        @JsonProperty("IoActApiKey") String ioActApiKey,
        @JsonProperty("IoWhitelist") List<String> ioWhitelist) {
}
