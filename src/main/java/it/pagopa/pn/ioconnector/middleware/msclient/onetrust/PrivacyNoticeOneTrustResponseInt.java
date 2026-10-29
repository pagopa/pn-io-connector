package it.pagopa.pn.ioconnector.middleware.msclient.onetrust;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivacyNoticeOneTrustResponseInt(List<PolicyVersion> versions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolicyVersion(Integer majorVersion) {}
}
