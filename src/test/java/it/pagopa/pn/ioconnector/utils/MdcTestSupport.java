package it.pagopa.pn.ioconnector.utils;

import org.mockito.stubbing.Answer;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class MdcTestSupport {

    public static final String XRAY_TRACE = "Root=1-5759e988-bd862e3fe1be46a994272793;Parent=53995c3f42cd8ad8;Sampled=1";

    private MdcTestSupport() {}

    public static Map<String, String> snapshot() {
        Map<String, String> copy = MDC.getCopyOfContextMap();
        return copy == null ? new HashMap<>() : copy;
    }

    public static <T> Answer<T> capturingInto(AtomicReference<Map<String, String>> reference, T result) {
        return invocation -> {
            reference.set(snapshot());
            return result;
        };
    }
}
