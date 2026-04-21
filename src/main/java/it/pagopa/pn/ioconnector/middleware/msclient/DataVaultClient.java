package it.pagopa.pn.ioconnector.middleware.msclient;

import lombok.CustomLog;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@CustomLog
@Component
public class DataVaultClient {

    /**
     * Deanonymizza il recipientTaxId.
     *
     * @param recipientTaxId cf anonimizzato proveniente dalla richiesta SEND
     * @return recipientTaxId deanonimizzato
     */
    public Mono<String> deanonymize(String recipientTaxId) {
        log.info("DataVaultClient.deanonymize");
        // TODO: chiamata a pn-data-vault per ottenere il CF deanonimizzato
        return Mono.just(recipientTaxId);
    }
}
