package it.pagopa.pn.ioconnector.service.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private SqsClient sqsClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private PnIoConnectorConfig config;
    @Mock private IOConnectorRequestDao requestDao;

    @InjectMocks private MessageService messageService;

    @BeforeEach
    void setUp() throws Exception {
        when(sqsClient.getQueueUrl(any(java.util.function.Consumer.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("http://localhost:4566/queue/pn-io-connector-send-queue").build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void handleSendRequest_accepted() {
        MessageResponse result = messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(result.getStatus()).isEqualTo(MessageResponse.StatusEnum.ACCEPTED);
        assertThat(result.getRequestId()).isEqualTo("REQ-001");
        assertThat(result.getxPagopaIoConCxId()).isEqualTo("pn-delivery-push");
    }

    private MessageRequest buildRequest() {
        return new MessageRequest()
            .requestId("REQ-001")
            .iun("IUN-001")
            .recipientTaxId("ANON-TAX")
            .senderTaxId("SENDER-TAX")
            .senderServiceId("SVC-001")
            .subject("Test")
            .markdown("body");
    }
}
