package it.pagopa.pn.ioconnector.config;

import lombok.CustomLog;
import lombok.Getter;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;


@Getter
@CustomLog
public class IoWhitelistChecker {

    private static final String WILDCARD = "*";

    private final Set<String> allowed;
    private final boolean enabled;

    public IoWhitelistChecker(List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty() || whitelist.contains(WILDCARD)) {
            this.allowed = Set.of();
            this.enabled = false;
        } else {
            this.allowed = whitelist.stream()
                    .filter(taxId -> taxId != null && !taxId.isBlank())
                    .map(IoWhitelistChecker::normalize)
                    .collect(Collectors.toUnmodifiableSet());
            this.enabled = !this.allowed.isEmpty();
        }
        log.info("IO whitelist - enabled={} size={}", this.enabled, this.allowed.size());
    }

    /**
     * @return true se il filtro è disattivo oppure se il codice fiscale è in lista.
     */
    public boolean isAllowed(String taxId) {
        if (!enabled) {
            return true;
        }
        return taxId != null && allowed.contains(normalize(taxId));
    }

    private static String normalize(String taxId) {
        return taxId.trim().toUpperCase(Locale.ROOT);
    }
}
