package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.localstack.LocalStackTestConfig;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @BeforeEach
    void purgeQueue() {
        String queueUrl = sqsClient.getQueueUrl(r -> r.queueName(config.getSqsSendQueueName())).queueUrl();
        sqsClient.purgeQueue(r -> r.queueUrl(queueUrl));
    }

    @Test
    void handleSendRequest_savesAcceptedRecordAndPublishesToSqs() {
        MessageRequest request = new MessageRequest()
                .requestId("INT-REQ-001")
                .iun("IUN-INT-001")
                .recipientTaxId("ANON-TAX-INT")
                .senderServiceId("SVC-INT-001")
                .subject("Integration Test Subject")
                .markdown("Integration test body")
                .pollingMaxMins(1440);

        Optional<MessageResponse> result = messageService.handleSendRequest("pn-delivery-push", request);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(MessageResponse.StatusEnum.ACCEPTED);

        Optional<IOConnectorRequestEntity> saved = requestDao.findById("INT-REQ-001");
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo("ACCEPTED");
        assertThat(saved.get().getRecipientTaxId()).isEqualTo("ANON-TAX-INT");
        assertThat(saved.get().getSenderServiceId()).isEqualTo("SVC-INT-001");
        assertThat(saved.get().getSubject()).isEqualTo("Integration Test Subject");
        assertThat(saved.get().getMarkdown()).isEqualTo("Integration test body");

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

    @Test
    void handleSendRequest_duplicateWithSamePayload_returns204() {
        MessageRequest request = new MessageRequest()
                .requestId("INT-REQ-IDEM-001")
                .iun("IUN-IDEM-001")
                .recipientTaxId("ANON-TAX-IDEM")
                .senderServiceId("SVC-IDEM-001")
                .subject("Idempotent Subject")
                .markdown("Idempotent body")
                .pollingMaxMins(1440);

        messageService.handleSendRequest("pn-delivery-push", request);

        Optional<MessageResponse> secondResult = messageService.handleSendRequest("pn-delivery-push", request);

        assertThat(secondResult).isEmpty();
    }

    @Test
    void handleSendRequest_duplicateWithDifferentPayload_throws409() {
        MessageRequest firstRequest = new MessageRequest()
                .requestId("INT-REQ-CONF-001")
                .iun("IUN-CONF-001")
                .recipientTaxId("ANON-TAX-CONF")
                .senderServiceId("SVC-CONF-001")
                .subject("Original Subject")
                .markdown("Original body")
                .pollingMaxMins(1440);

        messageService.handleSendRequest("pn-delivery-push", firstRequest);

        MessageRequest conflictingRequest = new MessageRequest()
                .requestId("INT-REQ-CONF-001")
                .iun("IUN-CONF-001")
                .recipientTaxId("ANON-TAX-CONF")
                .senderServiceId("SVC-CONF-001")
                .subject("Different Subject")
                .markdown("Original body")
                .pollingMaxMins(1440);

        assertThatThrownBy(() -> messageService.handleSendRequest("pn-delivery-push", conflictingRequest))
                .isInstanceOf(PnRuntimeException.class)
                .satisfies(ex -> assertThat(((PnRuntimeException) ex).getStatus()).isEqualTo(409));
    }
}
