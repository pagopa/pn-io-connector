package it.pagopa.pn.ioconnector.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "pn.io-connector")
@Import(SharedAutoConfiguration.class)
public class PnIoConnectorConfig {
    private String ioBaseUrl;
    private String dataVaultBaseUrl;
    private String dynamodbTableName;
    @NotBlank
    private String sqsSendQueueName;
    private String sqsPollingQueueName;
    private String eventBridgeBusName;
}
