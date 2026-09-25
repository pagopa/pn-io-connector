package it.pagopa.pn.ioconnector.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoWhitelistCheckerTest {

    private static final String CF = "RSSMRA80A01H501U";

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", " , ,"})
    void whitelistMissingOrEmpty_filterEnabled_everyoneBlocked(String whitelist) {
        IoWhitelistChecker checker = new IoWhitelistChecker(whitelist);

        assertTrue(checker.isEnabled());
        assertTrue(checker.getAllowed().isEmpty());
        assertFalse(checker.isAllowed(CF));
    }

    @Test
    void whitelistWithWildcard_filterDisabled_everyoneAllowed() {
        IoWhitelistChecker checker = new IoWhitelistChecker("*");

        assertFalse(checker.isEnabled());
        assertTrue(checker.isAllowed(CF));
        assertTrue(checker.isAllowed("QUALSIASI"));
    }

    @Test
    void wildcardAmongTaxIds_filterDisabled() {
        IoWhitelistChecker checker = new IoWhitelistChecker(CF + ",*");

        assertFalse(checker.isEnabled());
        assertTrue(checker.isAllowed("BNCGNN90C03L219Z"));
    }

    @Test
    void whitelistPopulated_allowsOnlyListedTaxIds() {
        IoWhitelistChecker checker = new IoWhitelistChecker(CF + ",VRDLGI75B02F205X");

        assertTrue(checker.isEnabled());
        assertTrue(checker.isAllowed(CF));
        assertTrue(checker.isAllowed("VRDLGI75B02F205X"));
        assertFalse(checker.isAllowed("BNCGNN90C03L219Z"));
        assertFalse(checker.isAllowed(null));
    }

    @Test
    void csvWithSpacesEmptyEntriesAndLowercase_isNormalized() {
        IoWhitelistChecker checker = new IoWhitelistChecker(" rssmra80a01h501u , ,VRDLGI75B02F205X ");

        assertTrue(checker.isEnabled());
        assertEquals(2, checker.getAllowed().size());
        assertTrue(checker.isAllowed(CF));
        assertTrue(checker.isAllowed(" vrdlgi75b02f205x "));
    }

}
