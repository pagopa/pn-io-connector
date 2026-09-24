package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PnIoConnectorConfigIoLegalSecretsTest {

    private static final String SECRET_NAME = "Pn-IO-Connector-Legal-Secrets";

    @Mock
    private SecretsManagerClient secretsManagerClient;

    private PnIoConnectorConfig config;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        config = new PnIoConnectorConfig();
        config.setLegalSecretsName(SECRET_NAME);
        // stesso builder usato da Spring Boot per l'ObjectMapper del contesto
        objectMapper = Jackson2ObjectMapperBuilder.json().build();
    }

    private void mockSecret(String secretString) {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(secretString).build());
    }

    private PnInternalException assertStartupFails() {
        PnInternalException ex = assertThrows(PnInternalException.class,
                () -> config.ioLegalSecrets(secretsManagerClient, objectMapper));
        assertEquals(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_LEGAL_SECRET_ERROR,
                ex.getProblem().getErrors().get(0).getCode());
        return ex;
    }

    @Test
    void completeSecret_isParsedWithDeclaredJsonNames() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin","IoWhiteList":"CF1,CF2"}""");

        IoLegalSecrets secrets = config.ioLegalSecrets(secretsManagerClient, objectMapper);

        assertEquals("key-legal", secrets.ioApiKey());
        assertEquals("key-optin", secrets.ioActApiKey());
        assertEquals("CF1,CF2", secrets.ioWhiteList());
    }

    @Test
    void whitelistInExternalRegistriesFormat_enablesFilter() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin","IoWhiteList":"BSTLSS72M29L736X, TRRMTT71R09A944J"}""");

        IoWhitelistChecker checker = config.ioWhitelistChecker(config.ioLegalSecrets(secretsManagerClient, objectMapper));

        assertTrue(checker.isEnabled());
        assertTrue(checker.isAllowed("BSTLSS72M29L736X"));
        assertTrue(checker.isAllowed("TRRMTT71R09A944J"));
        assertFalse(checker.isAllowed("RSSMRA80A01H501U"));
    }

    @Test
    void wildcardWhitelist_disablesFilter() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin","IoWhiteList":"*"}""");

        IoWhitelistChecker checker = config.ioWhitelistChecker(config.ioLegalSecrets(secretsManagerClient, objectMapper));

        assertFalse(checker.isEnabled());
        assertTrue(checker.isAllowed("RSSMRA80A01H501U"));
    }

    @Test
    void missingWhitelist_failsStartup() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin"}""");

        assertStartupFails();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " , "})
    void emptyWhitelist_failsStartup(String whitelist) {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin","IoWhiteList":"%s"}""".formatted(whitelist));

        assertStartupFails();
    }

    @Test
    void whitelistKeyWithDifferentCase_isIgnored_andFailsStartupListingTheKeysFound() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin","IoWhitelist":"CF1,CF2"}""");

        PnInternalException ex = assertStartupFails();

        assertTrue(ex.getProblem().getDetail().contains("[IoApiKey, IoActApiKey, IoWhitelist]"));
    }

    @Test
    void whitelistAsJsonArray_failsStartup() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin","IoWhiteList":["CF1","CF2"]}""");

        assertStartupFails();
    }

    @Test
    void missingIoApiKey_failsStartup() {
        mockSecret("""
                {"IoActApiKey":"key-optin","IoWhiteList":"*"}""");

        assertStartupFails();
    }

    @Test
    void blankIoActApiKey_failsStartup() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"  ","IoWhiteList":"*"}""");

        assertStartupFails();
    }
}
