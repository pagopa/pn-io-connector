package it.pagopa.pn.ioconnector.service.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.model.MessageSendRequest;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
        when(requestDao.findById(any())).thenReturn(Optional.empty());
        lenient().when(sqsClient.getQueueUrl(any(java.util.function.Consumer.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("http://localhost:4566/queue/pn-io-connector-send-queue").build());
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void handleSendRequest_accepted() throws Exception {
        Optional<MessageResponse> result = messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(MessageResponse.StatusEnum.ACCEPTED);
        assertThat(result.get().getRequestId()).isEqualTo("REQ-001");
        assertThat(result.get().getxPagopaIoConCxId()).isEqualTo("pn-delivery-push");
    }

    @Test
    void handleSendRequest_duplicateWithSamePayload_returns204() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .status("ACCEPTED")
                .build();
        when(requestDao.findById("REQ-001")).thenReturn(Optional.of(existing));

        Optional<MessageResponse> result = messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(result).isEmpty();
    }

    @Test
    void handleSendRequest_duplicateWithDifferentSubject_throws409() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .senderServiceId("SVC-001")
                .subject("Different Subject")
                .markdown("body")
                .status("ACCEPTED")
                .build();
        when(requestDao.findById("REQ-001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> messageService.handleSendRequest("pn-delivery-push", buildRequest()))
                .isInstanceOf(PnRuntimeException.class)
                .satisfies(ex -> assertThat(((PnRuntimeException) ex).getStatus()).isEqualTo(409));
    }

    @Test
    void handleSendRequest_duplicateDifferentCxId_throws409() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("another-cx-id")
                .iun("IUN-001")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .status("ACCEPTED")
                .build();
        when(requestDao.findById("REQ-001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> messageService.handleSendRequest("pn-delivery-push", buildRequest()))
                .isInstanceOf(PnRuntimeException.class)
                .satisfies(ex -> assertThat(((PnRuntimeException) ex).getStatus()).isEqualTo(409));
    }

    @Test
    void handleSendRequest_setsPollingMaxDateInSqsMessage() throws Exception {
        MessageRequest request = buildRequest().pollingMaxHours(24);

        messageService.handleSendRequest("pn-delivery-push", request);

        ArgumentCaptor<MessageSendRequest> sqsMsgCaptor = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(objectMapper).writeValueAsString(sqsMsgCaptor.capture());
        assertThat(sqsMsgCaptor.getValue().getPollingMaxDate()).isNotNull();
    }

    private MessageRequest buildRequest() {
        return new MessageRequest()
            .requestId("REQ-001")
            .iun("IUN-001")
            .recipientTaxId("ANON-TAX")
            .senderServiceId("SVC-001")
            .subject("Test")
            .markdown("body");
    }
}
