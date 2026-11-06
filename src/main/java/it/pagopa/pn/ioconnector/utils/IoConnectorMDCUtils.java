package it.pagopa.pn.ioconnector.utils;

import it.pagopa.pn.commons.utils.MDCUtils;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeNameForSends;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IoConnectorMDCUtils {

    public static final String MDC_IO_MESSAGE_ID_KEY = "ioMessageId";
    private static final Pattern XRAY_ROOT = Pattern.compile("Root=1-[0-9a-f]{8}-[0-9a-f]{24}");
    private static final Pattern XRAY_PARENT = Pattern.compile("Parent=[0-9a-f]{16}");
    private static final Pattern XRAY_SAMPLED = Pattern.compile("Sampled=[01?]");

    private IoConnectorMDCUtils() {}

    public static void initContext(String traceId, String awsMessageId, String requestId,
                                   String iun, String cxId, String ioMessageId) {
        clear();
        putIfNotNull(MDCUtils.MDC_TRACE_ID_KEY, traceId);
        putIfNotNull(MDCUtils.MDC_PN_CTX_MESSAGE_ID, awsMessageId);
        putIfNotNull(MDCUtils.MDC_PN_CTX_REQUEST_ID, requestId);
        putIfNotNull(MDCUtils.MDC_PN_IUN_KEY, iun);
        putIfNotNull(MDCUtils.MDC_CX_ID_KEY, cxId);
        putIfNotNull(MDC_IO_MESSAGE_ID_KEY, ioMessageId);
    }

    public static void clear() {
        MDCUtils.clearMDCKeys();
        MDC.remove(MDC_IO_MESSAGE_ID_KEY);
    }

    public static void putIoMessageId(String ioMessageId) {
        putIfNotNull(MDC_IO_MESSAGE_ID_KEY, ioMessageId);
    }

    public static Map<String, String> addToContext(String key, String value) {
        return addToContext(Collections.singletonMap(key, value));
    }

    public static Map<String, String> addToContext(Map<String, String> entries) {
        Map<String, String> previousValues = new HashMap<>();
        entries.forEach((key, value) -> {
            if (value != null) {
                previousValues.put(key, MDC.get(key));
                MDC.put(key, value);
            }
        });
        return previousValues;
    }

    public static void restoreContext(Map<String, String> previousValues) {
        previousValues.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }

    public static String currentXRayTraceHeader() {
        String traceId = MDC.get(MDCUtils.MDC_TRACE_ID_KEY);
        if (traceId == null) {
            return null;
        }
        String root = null;
        String parent = null;
        String sampled = null;
        for (String segment : traceId.split(";")) {
            if (root == null && XRAY_ROOT.matcher(segment).matches()) {
                root = segment;
            } else if (parent == null && XRAY_PARENT.matcher(segment).matches()) {
                parent = segment;
            } else if (sampled == null && XRAY_SAMPLED.matcher(segment).matches()) {
                sampled = segment;
            }
        }
        if (root == null) {
            return null;
        }
        return Stream.of(root, parent, sampled)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(";"));
    }

    public static SendMessageRequest.Builder withAwsTraceHeader(SendMessageRequest.Builder builder) {
        String traceHeader = currentXRayTraceHeader();
        if (traceHeader != null) {
            builder.messageSystemAttributes(Map.of(
                    MessageSystemAttributeNameForSends.AWS_TRACE_HEADER,
                    MessageSystemAttributeValue.builder().dataType("String").stringValue(traceHeader).build()));
        }
        return builder;
    }

    private static void putIfNotNull(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }
}
