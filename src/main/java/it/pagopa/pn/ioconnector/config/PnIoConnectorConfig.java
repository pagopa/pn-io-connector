package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import jakarta.validation.constraints.NotBlank;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.HashMap;
import java.util.Map;

@Data
@Validated
@Configuration
@Import(SharedAutoConfiguration.class)
@ConfigurationProperties(prefix = "pn.io-connector")
public class PnIoConnectorConfig {
    private String ioBaseUrl;
    private String dataVaultBaseUrl;
    private String dynamodbTableName;
    @NotBlank
    private String sqsSendQueueName;
    private String sqsPollingQueueName;
    private String eventBridgeBusName;
    private String secretsName;

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
}
