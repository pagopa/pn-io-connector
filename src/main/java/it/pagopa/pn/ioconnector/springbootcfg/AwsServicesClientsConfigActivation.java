package it.pagopa.pn.ioconnector.springbootcfg;

import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import io.awspring.cloud.sqs.listener.errorhandler.ExponentialBackoffErrorHandler;
import it.pagopa.pn.commons.configs.RuntimeMode;
import it.pagopa.pn.commons.configs.aws.AwsConfigs;
import it.pagopa.pn.commons.configs.aws.AwsServicesClientsConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.URI;

@Configuration
public class AwsServicesClientsConfigActivation extends AwsServicesClientsConfig {

    public AwsServicesClientsConfigActivation(AwsConfigs props) {
        super(props, RuntimeMode.PROD);
    }

    @Bean
    public SecretsManagerClient secretsManagerClient(AwsConfigs awsConfig) {
        var builder = SecretsManagerClient.builder();
        if (StringUtils.hasText(awsConfig.getProfileName())) {
            builder.credentialsProvider(ProfileCredentialsProvider.create(awsConfig.getProfileName()));
        }
        if (StringUtils.hasText(awsConfig.getRegionCode())) {
            builder.region(Region.of(awsConfig.getRegionCode()));
        }
        if (StringUtils.hasText(awsConfig.getEndpointUrl())) {
            builder.endpointOverride(URI.create(awsConfig.getEndpointUrl()));
        }
        return builder.build();
    }

    @Bean
    public EventBridgeClient eventBridgeSyncClient(AwsConfigs awsConfig) {
        var builder = EventBridgeClient.builder();
        if (StringUtils.hasText(awsConfig.getProfileName())) {
            builder.credentialsProvider(ProfileCredentialsProvider.create(awsConfig.getProfileName()));
        }
        if (StringUtils.hasText(awsConfig.getRegionCode())) {
            builder.region(Region.of(awsConfig.getRegionCode()));
        }
        if (StringUtils.hasText(awsConfig.getEndpointUrl())) {
            builder.endpointOverride(URI.create(awsConfig.getEndpointUrl()));
        }
        return builder.build();
    }

    @Bean
    public AsyncErrorHandler<Object> sqsExponentialBackoffErrorHandler() {
        return ExponentialBackoffErrorHandler.<Object>builder()
                .initialVisibilityTimeoutSeconds(30)
                .multiplier(2.0)
                .maxVisibilityTimeoutSeconds(3600)
                .build();
    }
}
