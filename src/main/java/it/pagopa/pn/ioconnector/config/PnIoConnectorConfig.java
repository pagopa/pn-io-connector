package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import jakarta.validation.constraints.NotBlank;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import lombok.CustomLog;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@CustomLog
@Validated
@Configuration
@Import(SharedAutoConfiguration.class)
@ConfigurationProperties(prefix = "pn.io-connector")
public class PnIoConnectorConfig {
    private String ioBaseUrl;
    private String ioLegalBaseUrl;
    private String dataVaultBaseUrl;
    private String dynamodbTableName;
    private String optinDynamodbTableName;
    @NotBlank
    private String sqsSendQueueName;
    private String sqsPollingQueueName;
    private String eventBridgeBusName;
    private String secretsName;
    private String legalSecretsName;
    private List<Integer> sendRetryPolicy;
    private int pollingIntervalMins;
    private Long pollingFixedIntervalSeconds;
    private String ioConfigurationId;
    private int ioOptinMinDays;

    /**
     * Comunicazioni Bonarie
     */
    @Bean
    public Map<String, String> apiKeyUseSecrets(SecretsManagerClient secretsManagerClient, ObjectMapper objectMapper) {
        Map<String, String> apiKeyUseMap = new HashMap<>();
        GetSecretValueResponse getSecretValueResponse;
        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretsName).build();
        try {
            getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
            apiKeyUseMap = objectMapper.readValue(
                getSecretValueResponse.secretString(),
                new TypeReference<>() {}
            );
        } catch (Exception e) {
            throw new PnInternalException("Failed to retrieve value from Secrets Manager: " + secretsName,
                    PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_SECRETSMANAGER_ERROR, e);
        }
        return apiKeyUseMap;
    }

    /**
     * Comunicazioni a valore Legale
     */
    @Bean
    public IoLegalSecrets ioLegalSecrets(SecretsManagerClient secretsManagerClient, ObjectMapper objectMapper) {
        IoLegalSecrets secrets;
        List<String> keys;
        GetSecretValueRequest request = GetSecretValueRequest.builder().secretId(legalSecretsName).build();
        try {
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            JsonNode json = objectMapper.readTree(response.secretString());
            keys = json.properties().stream().map(Map.Entry::getKey).toList();
            secrets = objectMapper.treeToValue(json, IoLegalSecrets.class);
        } catch (Exception e) {
            throw new PnInternalException("Failed to retrieve value from Secrets Manager: " + legalSecretsName,
                    PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_LEGAL_SECRET_ERROR, e);
        }

        if (secrets == null || !StringUtils.hasText(secrets.ioApiKey()) || !StringUtils.hasText(secrets.ioActApiKey())) {
            throw new PnInternalException("Incomplete legal secret, both IoApiKey and IoActApiKey are required: " + legalSecretsName
                    + " - keys found: " + keys, PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_LEGAL_SECRET_ERROR);
        }
        if (IoWhitelistChecker.parse(secrets.ioWhiteList()).isEmpty()) {
            throw new PnInternalException("Missing or empty IoWhiteList in legal secret " + legalSecretsName
                    + " - keys found: " + keys, PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_LEGAL_SECRET_ERROR);
        }
        log.info("Legal secret {} loaded - keys found: {}", legalSecretsName, keys);
        return secrets;
    }

    @Bean
    public IoWhitelistChecker ioWhitelistChecker(IoLegalSecrets ioLegalSecrets) {
        IoWhitelistChecker checker = new IoWhitelistChecker(ioLegalSecrets.ioWhiteList());
        if (checker.isEnabled()) {
            log.info("IO whitelist ENABLED - {} tax ids allowed", checker.getAllowed().size());
        } else {
            log.info("IO whitelist DISABLED - wildcard '*' configured");
        }
        return checker;
    }
}
