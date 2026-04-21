package it.pagopa.pn.ioconnector.middleware.msclient;

import lombok.CustomLog;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Risolve la ApiKey USE per un dato senderTaxId/serviceId.
 */
@CustomLog
@Component
public class IoApiKeyResolver {

    /**
     * Recupera la ApiKey USE per un dato senderTaxId e serviceId.
     * recupera ApiKey MANAGE dal vault SSM →
     * chiama GET /manage/services/{serviceId}/keys → restituisce ApiKey USE.
     */
    public Mono<String> resolveUseApiKey(String senderTaxId, String serviceId) {
        log.debug("Risoluzione ApiKey USE — senderTaxId={} serviceId={}", senderTaxId, serviceId);
        // TODO: recupero ApiKey MANAGE + chiamata ioBackendClient.getServiceUseKey()
        return Mono.just("");
    }
}
