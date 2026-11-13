package it.pagopa.pn.ioconnector.rest;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.api.FromIoMessageApi;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PreconditionContent;
import it.pagopa.pn.ioconnector.service.disclaimer.NotificationDisclaimerService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;

@CustomLog
@RestController
@RequiredArgsConstructor
public class NotificationDisclaimerController implements FromIoMessageApi {

    private final NotificationDisclaimerService notificationDisclaimerService;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<PreconditionContent> notificationDisclaimer(String iun, String xPagopaPnCxId) {
        return ResponseEntity.ok(notificationDisclaimerService.getNotificationDisclaimer(iun, xPagopaPnCxId));
    }
}
