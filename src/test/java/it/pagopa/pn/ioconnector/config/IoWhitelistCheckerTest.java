package it.pagopa.pn.ioconnector.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoWhitelistCheckerTest {

    private static final String CF = "RSSMRA80A01H501U";

    @Test
    void whitelistNull_filterDisabled_everyoneAllowed() {
        IoWhitelistChecker checker = new IoWhitelistChecker(null);

        assertFalse(checker.isEnabled());
        assertTrue(checker.isAllowed(CF));
        assertTrue(checker.isAllowed("QUALSIASI"));
    }

    @Test
    void whitelistEmpty_filterDisabled_everyoneAllowed() {
        IoWhitelistChecker checker = new IoWhitelistChecker(List.of());

        assertFalse(checker.isEnabled());
        assertTrue(checker.isAllowed(CF));
    }

    @Test
    void whitelistWithWildcard_filterDisabled_everyoneAllowed() {
        IoWhitelistChecker checker = new IoWhitelistChecker(List.of("*"));

        assertFalse(checker.isEnabled());
        assertTrue(checker.isAllowed(CF));
    }

    @Test
    void whitelistWithOnlyBlankEntries_filterDisabled() {
        IoWhitelistChecker checker = new IoWhitelistChecker(Arrays.asList("", "   ", null));

        assertFalse(checker.isEnabled());
        assertTrue(checker.isAllowed(CF));
    }

    @Test
    void whitelistPopulated_allowsOnlyListedTaxIds() {
        IoWhitelistChecker checker = new IoWhitelistChecker(List.of(CF, "VRDLGI75B02F205X"));

        assertTrue(checker.isEnabled());
        assertTrue(checker.isAllowed(CF));
        assertTrue(checker.isAllowed("VRDLGI75B02F205X"));
        assertFalse(checker.isAllowed("BNCGNN90C03L219Z"));
    }

}
