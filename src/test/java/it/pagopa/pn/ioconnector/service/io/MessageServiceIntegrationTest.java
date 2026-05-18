package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.localstack.LocalStackTestConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(LocalStackTestConfig.class)
class MessageServiceIntegrationTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private IOConnectorRequestDao requestDao;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private PnIoConnectorConfig config;

    @Test
    void handleSendRequest_savesAcceptedRecordAndPublishesToSqs() {
        MessageRequest request = new MessageRequest()
                .requestId("INT-REQ-001")
                .iun("IUN-INT-001")
                .recipientTaxId("ANON-TAX-INT")
                .senderTaxId("SENDER-TAX-INT")
                .senderServiceId("SVC-INT-001")
                .subject("Integration Test Subject")
                .markdown("Integration test body")
                .pollingMaxHours(24);

        MessageResponse response = messageService.handleSendRequest("pn-delivery-push", request);

        assertThat(response.getStatus()).isEqualTo(MessageResponse.StatusEnum.ACCEPTED);

        Optional<IOConnectorRequestEntity> saved = requestDao.findById("INT-REQ-001");
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo("ACCEPTED");

        assertThat(saved.get().getEventList()).isNotNull();
        assertThat(saved.get().getEventList()).isNotEmpty();
        assertThat(saved.get().getEventList().get(0).getStatus()).isEqualTo("ACCEPTED");

        String queueUrl = sqsClient.getQueueUrl(r -> r.queueName(config.getSqsSendQueueName())).queueUrl();
        ReceiveMessageResponse received = sqsClient.receiveMessage(r -> r
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5));

        assertThat(received.messages()).isNotEmpty();
        assertThat(received.messages().get(0).body()).contains("INT-REQ-001");
    }
}
