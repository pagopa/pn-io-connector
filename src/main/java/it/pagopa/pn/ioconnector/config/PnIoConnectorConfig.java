package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.Map;

@Data
@Configuration
@Import(SharedAutoConfiguration.class)
@ConfigurationProperties(prefix = "pn.io-connector")
public class PnIoConnectorConfig {
    private String ioBaseUrl;
    private String dataVaultBaseUrl;
    private String secretsName;

    @Bean
    public Map<String, String> apiKeyUseSecrets(SecretsManagerClient secretsManagerClient) {
        var response = secretsManagerClient.getSecretValue(
            GetSecretValueRequest.builder()
                .secretId(secretsName)
                .build()
        );
        try {
            return new ObjectMapper().readValue(
                response.secretString(),
                new TypeReference<>() {}
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Secrets Manager secret: " + secretsName, e);
        }
    }
}
