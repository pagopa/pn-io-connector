package it.pagopa.pn.ioconnector.service.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.model.OutcomePollingRequest;

import static it.pagopa.pn.commons.exceptions.PnExceptionsCodes.ERROR_CODE_PN_GENERIC_ERROR;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@CustomLog
@RequiredArgsConstructor
public class PollingQueueProducer {

    private final SqsClient sqsClient;
    private final PnIoConnectorConfig config;
    private final ObjectMapper objectMapper;

    public void publish(OutcomePollingRequest request) {
        String messageBody;
        try {
            messageBody = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new PnInternalException("Failed to serialize OutcomePollingRequest", ERROR_CODE_PN_GENERIC_ERROR, e);
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
