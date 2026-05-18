package it.pagopa.pn.ioconnector.config;

import it.pagopa.pn.commons.conf.SharedAutoConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@Configuration
@ConfigurationProperties(prefix = "pn.io-connector")
@Import(SharedAutoConfiguration.class)
public class PnIoConnectorConfig {
    private String ioBaseUrl;
    private String dataVaultBaseUrl;
    private String dynamodbTableName;
    private String sqsSendQueueName;
}
