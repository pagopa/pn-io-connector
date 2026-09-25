package it.pagopa.pn.ioconnector.config;

import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;


@Getter
public class IoWhitelistChecker {

    private static final String WILDCARD = "*";

    private final Set<String> allowed;
    private final boolean enabled;

    public IoWhitelistChecker(String whitelist) {
        List<String> entries = parse(whitelist);
        this.enabled = !entries.contains(WILDCARD);
        this.allowed = enabled
                ? entries.stream().map(IoWhitelistChecker::normalize).collect(Collectors.toUnmodifiableSet())
                : Set.of();
    }

    public boolean isAllowed(String taxId) {
        if (!enabled) {
            return true;
        }
        return taxId != null && allowed.contains(normalize(taxId));
    }

    static List<String> parse(String whitelist) {
        return List.of(StringUtils.tokenizeToStringArray(whitelist, ","));
    }

    private static String normalize(String taxId) {
        return taxId.trim().toUpperCase(Locale.ROOT);
    }
}
