package it.pagopa.pn.ioconnector.springbootcfg;

import it.pagopa.pn.commons.configs.aws.AwsConfigs;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClientBuilder;

import java.net.URI;

@Configuration
public class AwsClientsConfig {

    private final AwsConfigs props;

    public AwsClientsConfig(AwsConfigs props) {
        this.props = props;
    }

    @Bean
    public EventBridgeClient eventBridgeSyncClient() {
        EventBridgeClientBuilder builder = EventBridgeClient.builder();
        if (StringUtils.isNotBlank(props.getProfileName())) {
            builder.credentialsProvider(ProfileCredentialsProvider.create(props.getProfileName()));
        }
        if (StringUtils.isNotBlank(props.getRegionCode())) {
            builder.region(Region.of(props.getRegionCode()));
        }
        if (StringUtils.isNotBlank(props.getEndpointUrl())) {
            builder.endpointOverride(URI.create(props.getEndpointUrl()));
        }
        return builder.build();
    }
}
