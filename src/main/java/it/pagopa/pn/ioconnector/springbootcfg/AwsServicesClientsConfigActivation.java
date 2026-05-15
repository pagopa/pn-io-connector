package it.pagopa.pn.ioconnector.springbootcfg;

import it.pagopa.pn.commons.configs.RuntimeMode;
import it.pagopa.pn.commons.configs.aws.AwsConfigs;
import it.pagopa.pn.commons.configs.aws.AwsServicesClientsConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.URI;

@Configuration
public class AwsServicesClientsConfigActivation extends AwsServicesClientsConfig {

    public AwsServicesClientsConfigActivation(AwsConfigs props) {
        super(props, RuntimeMode.PROD);
    }

    @Bean
    public SecretsManagerClient secretsManagerClient(AwsConfigs awsConfig) {
        var builder = SecretsManagerClient.builder().region(Region.of(awsConfig.getRegionCode()));
        if (StringUtils.hasText(awsConfig.getEndpointUrl())) {
            builder.endpointOverride(URI.create(awsConfig.getEndpointUrl()));
        }
        return builder.build();
    }
}
