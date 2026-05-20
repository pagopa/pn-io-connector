package it.pagopa.pn.ioconnector.localstack;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import lombok.CustomLog;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.io.IOException;
import java.time.Duration;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;

@TestConfiguration
@CustomLog
public class LocalStackTestConfig {

    static DockerImageName dockerImageName = DockerImageName.parse("localstack/localstack:1.0.4");
    static LocalStackContainer localStack =
            new LocalStackContainer(dockerImageName)
                    .withServices(DYNAMODB, SQS, SECRETSMANAGER)
                    .withEnv("USE_SSL", "false")
                    .withServices(DYNAMODB, SQS, SECRETSMANAGER)
                    .withClasspathResourceMapping("testcontainers/init.sh", "/docker-entrypoint-initaws.d/init.sh", BindMode.READ_ONLY)
                    .withClasspathResourceMapping("testcontainers/credentials", "/root/.aws/credentials", BindMode.READ_ONLY)
                    .waitingFor(Wait.forLogMessage(".*Initialization terminated.*", 1)
                            .withStartupTimeout(Duration.ofMinutes(5)));

    static {
        localStack.start();
        System.setProperty("aws.endpoint-url", localStack.getEndpointOverride(DYNAMODB).toString());
        System.setProperty("test.aws.dynamodb.endpoint", localStack.getEndpointOverride(DYNAMODB).toString());
        System.setProperty("aws.endpoint-url-sqs", localStack.getEndpointOverride(SQS).toString());
        try {
            System.setProperty("aws.sharedCredentialsFile",
                    new ClassPathResource("testcontainers/credentials").getFile().getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .endpointOverride(localStack.getEndpointOverride(SQS))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("TEST", "TEST")))
                .region(Region.US_EAST_1)
                .build();
    }

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.<Object>builder()
                .configure(opts -> opts.autoStartup(false))
                .sqsAsyncClient(sqsAsyncClient)
                .build();
    }
}
