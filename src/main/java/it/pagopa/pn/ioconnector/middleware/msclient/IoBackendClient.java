package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.log.PnLogger;
import lombok.CustomLog;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@CustomLog
@Component
public class IoBackendClient {

    /**
     * POST /profiles — verifica profilo.
     *
     * @return boolean
     */
    public Mono<Boolean> checkUserProfile(String fiscalCode, String apiKeyUse) {
        log.info("IoBackendClient.checkUserProfile — mock sender_allowed=true");
        // TODO: chiamata a POST /profiles su IO
        return Mono.just(true);
    }

    /**
     * POST /messages — invio messaggio.
     *
     * @return ioMessageId assegnato da IO (campo "id" della risposta 201)
     */
    public Mono<String> sendMessage(Object request, String apiKeyUse) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "sendMessage");
        // TODO: chiamata a POST /sendMessages su IO
        return Mono.empty();
    }

    /**
     * GET /messages/{id} — stato messaggio.
     *
     * @return stato
     */
    public Mono<String> getMessageStatus(String ioMessageId, String apiKeyUse) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "getMessageStatus");
        // TODO chiamata a GET /messages su IO
        return Mono.empty();
    }

    /**
     * GET /manage/services/{serviceId}/keys — recupero ApiKey USE.
     */
    public Mono<String> getServiceUseKey(String serviceId, String manageApiKey) {
        log.logInvokingExternalService(PnLogger.EXTERNAL_SERVICES.IO, "getServiceKeys");
        // TODO chiamata a GET /manage/services/ su IO
        return Mono.empty();
    }
}
