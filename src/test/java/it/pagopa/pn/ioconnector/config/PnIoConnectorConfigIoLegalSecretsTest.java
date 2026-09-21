package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        objectMapper = new ObjectMapper();
    }

    private void mockSecret(String secretString) {
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(secretString).build());
    }

    @Test
    void completeSecret_isParsedWithDeclaredJsonNames() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin","IoWhitelist":["CF1","CF2"]}""");

        IoLegalSecrets secrets = config.ioLegalSecrets(secretsManagerClient, objectMapper);

        assertEquals("key-legal", secrets.ioApiKey());
        assertEquals("key-optin", secrets.ioActApiKey());
        assertEquals(List.of("CF1", "CF2"), secrets.ioWhitelist());
    }

    @Test
    void missingWhitelist_isLegitimate_andMeansFilterDisabled() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"key-optin"}""");

        IoLegalSecrets secrets = config.ioLegalSecrets(secretsManagerClient, objectMapper);

        assertNull(secrets.ioWhitelist());
        assertEquals(false, new IoWhitelistChecker(secrets.ioWhitelist()).isEnabled());
    }

    @Test
    void missingIoApiKey_failsStartup() {
        mockSecret("""
                {"IoActApiKey":"key-optin"}""");

        PnInternalException ex = assertThrows(PnInternalException.class,
                () -> config.ioLegalSecrets(secretsManagerClient, objectMapper));

        assertEquals(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_LEGAL_SECRET_ERROR,
                ex.getProblem().getErrors().get(0).getCode());
    }

    @Test
    void blankIoActApiKey_failsStartup() {
        mockSecret("""
                {"IoApiKey":"key-legal","IoActApiKey":"  "}""");

        PnInternalException ex = assertThrows(PnInternalException.class,
                () -> config.ioLegalSecrets(secretsManagerClient, objectMapper));

        assertEquals(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_LEGAL_SECRET_ERROR,
                ex.getProblem().getErrors().get(0).getCode());
    }
}
