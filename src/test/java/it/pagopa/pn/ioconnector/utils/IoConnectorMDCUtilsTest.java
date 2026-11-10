package it.pagopa.pn.ioconnector.utils;

import it.pagopa.pn.commons.utils.MDCUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeNameForSends;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IoConnectorMDCUtilsTest {

    @BeforeEach
    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void initContext_setsAllKeys() {
        IoConnectorMDCUtils.initContext("trace-1", "msg-1", "REQ-1", "IUN-1", "CX-1", "IO-1");

        assertThat(MdcTestSupport.snapshot())
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, "trace-1")
                .containsEntry(MDCUtils.MDC_PN_CTX_MESSAGE_ID, "msg-1")
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-1")
                .containsEntry(MDCUtils.MDC_PN_IUN_KEY, "IUN-1")
                .containsEntry(MDCUtils.MDC_CX_ID_KEY, "CX-1")
                .containsEntry(IoConnectorMDCUtils.MDC_IO_MESSAGE_ID_KEY, "IO-1");
    }

    @Test
    void initContext_skipsNullValues() {
        IoConnectorMDCUtils.initContext(null, "msg-1", "REQ-1", null, null, null);

        assertThat(MdcTestSupport.snapshot())
                .containsEntry(MDCUtils.MDC_PN_CTX_MESSAGE_ID, "msg-1")
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-1")
                .doesNotContainKeys(MDCUtils.MDC_TRACE_ID_KEY, MDCUtils.MDC_PN_IUN_KEY,
                        MDCUtils.MDC_CX_ID_KEY, IoConnectorMDCUtils.MDC_IO_MESSAGE_ID_KEY);
    }

    @Test
    void initContext_clearsPreviousContext() {
        MDC.put(MDCUtils.MDC_PN_IUN_KEY, "STALE-IUN");
        MDC.put(IoConnectorMDCUtils.MDC_IO_MESSAGE_ID_KEY, "STALE-IO");

        IoConnectorMDCUtils.initContext("trace-1", "msg-1", "REQ-1", null, null, null);

        assertThat(MdcTestSupport.snapshot())
                .doesNotContainKeys(MDCUtils.MDC_PN_IUN_KEY, IoConnectorMDCUtils.MDC_IO_MESSAGE_ID_KEY);
    }

    @Test
    void clear_removesPnKeysAndIoMessageId() {
        IoConnectorMDCUtils.initContext("trace-1", "msg-1", "REQ-1", "IUN-1", "CX-1", "IO-1");

        IoConnectorMDCUtils.clear();

        assertThat(MdcTestSupport.snapshot()).isEmpty();
    }

    @Test
    void addToContext_keepsFilterKeys() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");
        MDC.put(MDCUtils.MDC_PN_CX_TYPE_KEY, "PA");

        IoConnectorMDCUtils.addToContext(Map.of(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-1"));

        assertThat(MdcTestSupport.snapshot())
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace")
                .containsEntry(MDCUtils.MDC_PN_CX_TYPE_KEY, "PA")
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-1");
    }

    @Test
    void addToContext_skipsNullValues() {
        Map<String, String> entries = new HashMap<>();
        entries.put(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-1");
        entries.put(MDCUtils.MDC_PN_IUN_KEY, null);

        IoConnectorMDCUtils.addToContext(entries);

        assertThat(MdcTestSupport.snapshot())
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-1")
                .doesNotContainKey(MDCUtils.MDC_PN_IUN_KEY);
    }

    @Test
    void restoreContext_removesAddedKeysAndKeepsFilterKeys() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");

        Map<String, String> previous = IoConnectorMDCUtils.addToContext(
                Map.of(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-1", MDCUtils.MDC_PN_IUN_KEY, "IUN-1"));
        IoConnectorMDCUtils.restoreContext(previous);

        assertThat(MdcTestSupport.snapshot())
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace")
                .doesNotContainKeys(MDCUtils.MDC_PN_CTX_REQUEST_ID, MDCUtils.MDC_PN_IUN_KEY);
    }

    @Test
    void restoreContext_restoresOverwrittenValue() {
        MDC.put(MDCUtils.MDC_CX_ID_KEY, "header-cx-id");

        Map<String, String> previous = IoConnectorMDCUtils.addToContext(Map.of(MDCUtils.MDC_CX_ID_KEY, "internal-id"));
        assertThat(MDC.get(MDCUtils.MDC_CX_ID_KEY)).isEqualTo("internal-id");
        IoConnectorMDCUtils.restoreContext(previous);

        assertThat(MDC.get(MDCUtils.MDC_CX_ID_KEY)).isEqualTo("header-cx-id");
    }

    private static final String ROOT = "Root=1-5759e988-bd862e3fe1be46a994272793";
    private static final String SELF = "Self=1-67891233-abcdef012345678912345678";

    @Test
    void currentXRayTraceHeader_completeHeader_isPropagatedWhole() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, ROOT + ";Parent=53995c3f42cd8ad8;Sampled=1");

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader())
                .isEqualTo(ROOT + ";Parent=53995c3f42cd8ad8;Sampled=1");
    }

    @Test
    void currentXRayTraceHeader_sampledUnknown_isPropagatedWhole() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, ROOT + ";Parent=53995c3f42cd8ad8;Sampled=?");

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader())
                .isEqualTo(ROOT + ";Parent=53995c3f42cd8ad8;Sampled=?");
    }

    @Test
    void currentXRayTraceHeader_rootOnly_isPropagated() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, ROOT);

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader()).isEqualTo(ROOT);
    }

    @Test
    void currentXRayTraceHeader_albFormat_keepsOnlyRoot() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, SELF + ";" + ROOT + ";CalledFrom=app");

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader()).isEqualTo(ROOT);
    }

    @Test
    void currentXRayTraceHeader_segmentsInAnyOrder_areEmittedAsRootParentSampled() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "Sampled=0;Parent=53995c3f42cd8ad8;" + ROOT);

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader())
                .isEqualTo(ROOT + ";Parent=53995c3f42cd8ad8;Sampled=0");
    }

    @Test
    void currentXRayTraceHeader_malformedParent_isDiscarded() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, ROOT + ";Parent=not-a-parent;Sampled=1");

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader()).isEqualTo(ROOT + ";Sampled=1");
    }

    @Test
    void currentXRayTraceHeader_rootWithMalformedParentOnly_isRootOnly() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, ROOT + ";Parent=not-a-parent");

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader()).isEqualTo(ROOT);
    }

    @Test
    void currentXRayTraceHeader_extraSegmentsAfterRoot_areDiscarded() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY,
                ROOT + ";Parent=53995c3f42cd8ad8;Sampled=1;Lineage=a87bd80c:0;CalledFrom=app;TotalTimeSoFar=112ms");

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader())
                .isEqualTo(ROOT + ";Parent=53995c3f42cd8ad8;Sampled=1");
    }

    @Test
    void currentXRayTraceHeader_malformedRoot_isNull() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "Root=1-zzzz-123;Parent=53995c3f42cd8ad8;Sampled=1");

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader()).isNull();
    }

    @Test
    void currentXRayTraceHeader_truncatedRoot_isNull() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "Root=1-5759e988-bd862e3fe1be46a9942727");

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader()).isNull();
    }

    @Test
    void currentXRayTraceHeader_fallbackTraceId_isNull() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "trace_id:" + java.util.UUID.randomUUID());

        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader()).isNull();
    }

    @Test
    void currentXRayTraceHeader_noTraceId_isNull() {
        assertThat(IoConnectorMDCUtils.currentXRayTraceHeader()).isNull();
    }

    @Test
    void withAwsTraceHeader_albFormat_setsRootOnlyAttribute() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, SELF + ";" + ROOT + ";CalledFrom=app");

        SendMessageRequest request = IoConnectorMDCUtils.withAwsTraceHeader(SendMessageRequest.builder()).build();

        assertThat(request.messageSystemAttributes().get(MessageSystemAttributeNameForSends.AWS_TRACE_HEADER).stringValue())
                .isEqualTo(ROOT);
    }

    @Test
    void withAwsTraceHeader_fallbackTraceId_doesNotSetAttribute() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "trace_id:" + java.util.UUID.randomUUID());

        SendMessageRequest request = IoConnectorMDCUtils.withAwsTraceHeader(SendMessageRequest.builder()).build();

        assertThat(request.messageSystemAttributes()).doesNotContainKey(MessageSystemAttributeNameForSends.AWS_TRACE_HEADER);
    }

    @Test
    void withAwsTraceHeader_noTraceId_doesNotSetAttribute() {
        SendMessageRequest request = IoConnectorMDCUtils.withAwsTraceHeader(SendMessageRequest.builder()).build();

        assertThat(request.messageSystemAttributes()).doesNotContainKey(MessageSystemAttributeNameForSends.AWS_TRACE_HEADER);
    }
}
