package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IoServiceConfiguration(String configurationId, String organizationFiscalCode) {
}
