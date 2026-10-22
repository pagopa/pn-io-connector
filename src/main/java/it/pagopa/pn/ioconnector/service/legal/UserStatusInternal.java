package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;

import java.util.List;


public record UserStatusInternal(UserStatusResponse.StatusEnum status, List<String> preferredLanguages) {
}
