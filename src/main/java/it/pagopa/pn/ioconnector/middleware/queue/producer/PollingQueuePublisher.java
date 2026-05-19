package it.pagopa.pn.ioconnector.middleware.queue.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;
import lombok.CustomLog;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@CustomLog
public class PollingQueuePublisher {

    private final SqsClient sqsClient;
    private final PnIoConnectorConfig config;
    private final ObjectMapper objectMapper;

    public PollingQueuePublisher(SqsClient sqsClient, PnIoConnectorConfig config, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public void publish(OutcomePollingRequest request) {
        String messageBody;
        try {
            messageBody = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OutcomePollingRequest", e);
        }
        GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                .queueName(config.getSqsPollingQueueName())
                .build();
        String queueUrl = sqsClient.getQueueUrl(getQueueUrlRequest).queueUrl();
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();
        sqsClient.sendMessage(sendMessageRequest);
    }
}
