package it.pagopa.pn.ioconnector.service.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Attachment;
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

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.PaymentData;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.utils.LogEventCaptor;
import it.pagopa.pn.ioconnector.utils.MdcTestSupport;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.mockito.invocation.Invocation;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeNameForSends;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockingDetails;
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
        when(requestDao.findByIdConsistentRead(any())).thenReturn(Optional.empty());
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
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead("REQ-001")).thenReturn(Optional.of(existing));

        Optional<MessageResponse> result = messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(result).isEmpty();
    }

    @Test
    void handleSendRequest_duplicateWithDifferentSubject_throws409() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Different Subject")
                .markdown("body")
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead("REQ-001")).thenReturn(Optional.of(existing));

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
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead("REQ-001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> messageService.handleSendRequest("pn-delivery-push", buildRequest()))
                .isInstanceOf(PnRuntimeException.class)
                .satisfies(ex -> assertThat(((PnRuntimeException) ex).getStatus()).isEqualTo(409));
    }

    @Test
    void handleSendRequest_duplicateWithDifferentAttachments_throws409() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .attachments(List.of(
                        IOConnectorRequestEntity.Attachment.builder().fileKey("key1").build(),
                        IOConnectorRequestEntity.Attachment.builder().fileKey("key-OTHER").build()))
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead("REQ-001")).thenReturn(Optional.of(existing));

        MessageRequest request = buildRequest()
                .attachments(List.of(
                        new Attachment().fileKey("key1"),
                        new Attachment().fileKey("key2")));

        assertThatThrownBy(() -> messageService.handleSendRequest("pn-delivery-push", request))
                .isInstanceOf(PnRuntimeException.class)
                .satisfies(ex -> assertThat(((PnRuntimeException) ex).getStatus()).isEqualTo(409));
    }

    @Test
    void handleSendRequest_duplicateWithSameAttachments_returns204() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .attachments(List.of(
                        IOConnectorRequestEntity.Attachment.builder().fileKey("key1").build(),
                        IOConnectorRequestEntity.Attachment.builder().fileKey("key2").build()))
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead("REQ-001")).thenReturn(Optional.of(existing));

        MessageRequest request = buildRequest()
                .attachments(List.of(
                        new Attachment().fileKey("key1"),
                        new Attachment().fileKey("key2")));

        assertThat(messageService.handleSendRequest("pn-delivery-push", request)).isEmpty();
    }

    @Test
    void handleSendRequest_mapsAttachmentNameToSqsAndEntity() throws Exception {
        MessageRequest request = buildRequest()
                .attachments(List.of(
                        new Attachment().id("att-1").fileKey("key1.pdf").name("documento.pdf")));

        messageService.handleSendRequest("pn-delivery-push", request);

        ArgumentCaptor<MessageSendRequest> sqsMsgCaptor = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(objectMapper).writeValueAsString(sqsMsgCaptor.capture());
        assertThat(sqsMsgCaptor.getValue().getAttachments()).hasSize(1);
        assertThat(sqsMsgCaptor.getValue().getAttachments().get(0).getName()).isEqualTo("documento.pdf");

        ArgumentCaptor<IOConnectorRequestEntity> entityCaptor = ArgumentCaptor.forClass(IOConnectorRequestEntity.class);
        verify(requestDao).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getAttachments()).hasSize(1);
        assertThat(entityCaptor.getValue().getAttachments().get(0).getName()).isEqualTo("documento.pdf");
    }

    @Test
    void handleSendRequest_duplicateWithDifferentAttachmentName_throws409() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .attachments(List.of(
                        IOConnectorRequestEntity.Attachment.builder().id("att-1").fileKey("key1").name("nome-A.pdf").build()))
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead("REQ-001")).thenReturn(Optional.of(existing));

        MessageRequest request = buildRequest()
                .attachments(List.of(
                        new Attachment().id("att-1").fileKey("key1").name("nome-B.pdf")));

        assertThatThrownBy(() -> messageService.handleSendRequest("pn-delivery-push", request))
                .isInstanceOf(PnRuntimeException.class)
                .satisfies(ex -> assertThat(((PnRuntimeException) ex).getStatus()).isEqualTo(409));
    }


    @Test
    void handleSendRequest_duplicateWithDifferentInvalidAfterDueDate_throws409() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .paymentData(IOConnectorRequestEntity.PaymentData.builder()
                        .amount(100)
                        .noticeCode("302000100000019421")
                        .creditorTaxId("77777777777")
                        .invalidAfterDueDate(false)
                        .build())
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead("REQ-001")).thenReturn(Optional.of(existing));

        MessageRequest request = buildRequest()
                .paymentData(new PaymentData()
                        .amount(100)
                        .noticeCode("302000100000019421")
                        .creditorTaxId("77777777777")
                        .invalidAfterDueDate(true));   // diverso

        assertThatThrownBy(() -> messageService.handleSendRequest("pn-delivery-push", request))
                .isInstanceOf(PnRuntimeException.class)
                .satisfies(ex -> assertThat(((PnRuntimeException) ex).getStatus()).isEqualTo(409));
    }

    @Test
    void handleSendRequest_duplicateWithSameInvalidAfterDueDate_returns204() {
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Test")
                .markdown("body")
                .paymentData(IOConnectorRequestEntity.PaymentData.builder()
                        .amount(100)
                        .noticeCode("302000100000019421")
                        .creditorTaxId("77777777777")
                        .invalidAfterDueDate(true)
                        .build())
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead("REQ-001")).thenReturn(Optional.of(existing));

        MessageRequest request = buildRequest()
                .paymentData(new PaymentData()
                        .amount(100)
                        .noticeCode("302000100000019421")
                        .creditorTaxId("77777777777")
                        .invalidAfterDueDate(true));

        assertThat(messageService.handleSendRequest("pn-delivery-push", request)).isEmpty();
    }

    @Test
    void handleSendRequest_setsPollingMaxDateInSqsMessage() throws Exception {
        MessageRequest request = buildRequest().pollingMaxMins(1440);

        messageService.handleSendRequest("pn-delivery-push", request);

        ArgumentCaptor<MessageSendRequest> sqsMsgCaptor = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(objectMapper).writeValueAsString(sqsMsgCaptor.capture());
        assertThat(sqsMsgCaptor.getValue().getPollingMaxDate()).isNotNull();
    }

    @Test
    void handleSendRequest_withoutPollingMaxMins_usesConfigDefault() throws Exception {
        when(config.getPollingIntervalMins()).thenReturn(1440);
        MessageRequest request = buildRequest();

        Instant before = Instant.now();
        messageService.handleSendRequest("pn-delivery-push", request);
        Instant after = Instant.now();

        ArgumentCaptor<MessageSendRequest> sqsMsgCaptor = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(objectMapper).writeValueAsString(sqsMsgCaptor.capture());
        assertThat(sqsMsgCaptor.getValue().getPollingMaxDate())
                .isBetween(before.plus(1440, ChronoUnit.MINUTES), after.plus(1440, ChronoUnit.MINUTES));
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

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void handleSendRequest_setsMdcRequestIdIunCxId() {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        when(requestDao.findByIdConsistentRead(any()))
                .thenAnswer(MdcTestSupport.capturingInto(captured, Optional.<IOConnectorRequestEntity>empty()));

        messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(captured.get())
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-001")
                .containsEntry(MDCUtils.MDC_PN_IUN_KEY, "IUN-001")
                .containsEntry(MDCUtils.MDC_CX_ID_KEY, "ANON-TAX")
                .doesNotContainKey("requestId");
    }

    @Test
    void handleSendRequest_cleansAddedMdcKeys() {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        when(requestDao.findByIdConsistentRead(any()))
                .thenAnswer(MdcTestSupport.capturingInto(captured, Optional.<IOConnectorRequestEntity>empty()));

        messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(captured.get()).containsKey(MDCUtils.MDC_PN_CTX_REQUEST_ID);
        assertThat(MdcTestSupport.snapshot())
                .doesNotContainKeys(MDCUtils.MDC_PN_CTX_REQUEST_ID, MDCUtils.MDC_PN_IUN_KEY,
                        MDCUtils.MDC_CX_ID_KEY, "requestId");
    }

    @Test
    void handleSendRequest_conflict_cleansAddedMdcKeys() {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        IOConnectorRequestEntity existing = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .iun("IUN-001")
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001")
                .subject("Different Subject")
                .markdown("body")
                .status("ACCEPTED")
                .build();
        when(requestDao.findByIdConsistentRead(any()))
                .thenAnswer(MdcTestSupport.capturingInto(captured, Optional.of(existing)));

        assertThatThrownBy(() -> messageService.handleSendRequest("pn-delivery-push", buildRequest()))
                .isInstanceOf(PnRuntimeException.class);

        assertThat(captured.get()).containsKey(MDCUtils.MDC_PN_CTX_REQUEST_ID);
        assertThat(MdcTestSupport.snapshot())
                .doesNotContainKeys(MDCUtils.MDC_PN_CTX_REQUEST_ID, MDCUtils.MDC_PN_IUN_KEY,
                        MDCUtils.MDC_CX_ID_KEY, "requestId");
    }

    @Test
    void handleSendRequest_keepsTraceIdFromFilter() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        when(requestDao.findByIdConsistentRead(any()))
                .thenAnswer(MdcTestSupport.capturingInto(captured, Optional.<IOConnectorRequestEntity>empty()));

        messageService.handleSendRequest("pn-delivery-push", buildRequest());

        assertThat(captured.get())
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace")
                .containsKey(MDCUtils.MDC_PN_CTX_REQUEST_ID);
        assertThat(MDC.get(MDCUtils.MDC_TRACE_ID_KEY)).isEqualTo("filter-trace");
    }

    @Test
    void handleSendRequest_logsSqsInsertion() {
        lenient().when(config.getSqsSendQueueName()).thenReturn("pn-io-connector-send-queue");

        try (LogEventCaptor logs = LogEventCaptor.attachTo(MessageService.class)) {
            messageService.handleSendRequest("pn-delivery-push", buildRequest());

            assertThat(logs.hasEvent(Level.DEBUG, "Inserting data")).isTrue();
            assertThat(logs.hasEvent(Level.INFO, "Inserted data in SQS pn-io-connector-send-queue")).isTrue();
        }
    }

    @Test
    void handleSendRequest_setsAwsTraceHeader() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, MdcTestSupport.XRAY_TRACE);

        messageService.handleSendRequest("pn-delivery-push", buildRequest());

        SendMessageRequest sent = lastSentRequest();
        assertThat(sent.messageSystemAttributes())
                .containsKey(MessageSystemAttributeNameForSends.AWS_TRACE_HEADER);
        assertThat(sent.messageSystemAttributes().get(MessageSystemAttributeNameForSends.AWS_TRACE_HEADER).stringValue())
                .isEqualTo(MdcTestSupport.XRAY_TRACE);
    }

    @Test
    void handleSendRequest_startingProcessLogCarriesDomainMdc() {
        try (LogEventCaptor logs = LogEventCaptor.attachTo(MessageService.class)) {
            messageService.handleSendRequest("pn-delivery-push", buildRequest());

            assertThat(logs.mdcAt("Starting process"))
                    .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-001")
                    .containsEntry(MDCUtils.MDC_PN_IUN_KEY, "IUN-001")
                    .containsEntry(MDCUtils.MDC_CX_ID_KEY, "ANON-TAX");
        }
    }

    @SuppressWarnings("unchecked")
    private SendMessageRequest lastSentRequest() {
        Invocation invocation = mockingDetails(sqsClient).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("sendMessage"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("sendMessage not invoked"));
        Object argument = invocation.getArgument(0);
        if (argument instanceof SendMessageRequest request) {
            return request;
        }
        SendMessageRequest.Builder builder = SendMessageRequest.builder();
        ((Consumer<SendMessageRequest.Builder>) argument).accept(builder);
        return builder.build();
    }
}
