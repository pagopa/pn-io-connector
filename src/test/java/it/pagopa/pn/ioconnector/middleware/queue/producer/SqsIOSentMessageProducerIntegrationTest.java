package it.pagopa.pn.ioconnector.middleware.queue.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.localstack.LocalStackTestConfig;
import it.pagopa.pn.ioconnector.service.legal.SendIOSentMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(LocalStackTestConfig.class)
class SqsIOSentMessageProducerIntegrationTest {

    @Autowired
    private SendIOSentMessageService sendIOSentMessageService;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private PnIoConnectorConfig config;

    @Autowired
    private ObjectMapper objectMapper;

    private String queueUrl;

    @BeforeEach
    void purgeQueue() {
        queueUrl = sqsClient.getQueueUrl(r -> r.queueName(config.getSqsDeliveryPushInputQueueName())).queueUrl();
        sqsClient.purgeQueue(r -> r.queueUrl(queueUrl));
    }

    @Test
    void sendIOSentMessageNotification_publishesFifoEventOnDeliveryPushQueue() throws Exception {
        Instant sentDate = Instant.parse("2026-03-01T10:15:30Z");

        sendIOSentMessageService.sendIOSentMessageNotification("IUN-INT-001", 2, "PF-internal-INT", sentDate);

        // la coda ha DelaySeconds=2: si attende con long polling
        ReceiveMessageResponse received = sqsClient.receiveMessage(r -> r
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(10)
                .messageAttributeNames("All")
                .attributeNamesWithStrings("All"));

        assertThat(received.messages()).hasSize(1);
        Message message = received.messages().get(0);

        Map<MessageSystemAttributeName, String> systemAttributes = message.attributes();
        assertThat(systemAttributes)
                .containsEntry(MessageSystemAttributeName.MESSAGE_GROUP_ID, "external-registry")
                .containsEntry(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID, "IUN-INT-001_iosentmessage_2");

        assertThat(message.messageAttributes().get("eventType").stringValue()).isEqualTo("SEND_IO_MESSAGE_REQUEST");
        assertThat(message.messageAttributes().get("iun").stringValue()).isEqualTo("IUN-INT-001");
        assertThat(message.messageAttributes().get("eventId").stringValue()).isEqualTo("IUN-INT-001_iosentmessage_2");
        assertThat(message.messageAttributes().get("publisher").stringValue()).isEqualTo("EXTERNAL_REGISTRY");
        assertThat(message.messageAttributes().get("createdAt").stringValue()).isNotBlank();

        JsonNode body = objectMapper.readTree(message.body());
        assertThat(body.get("iun").asText()).isEqualTo("IUN-INT-001");
        assertThat(body.get("recIndex").asInt()).isEqualTo(2);
        assertThat(body.get("internalId").asText()).isEqualTo("PF-internal-INT");
        assertThat(Instant.parse(body.get("sendDate").asText())).isEqualTo(sentDate);
    }
}
