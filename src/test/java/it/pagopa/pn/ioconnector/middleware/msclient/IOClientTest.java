package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.DefaultApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.CreatedMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ExternalMessageResponseWithContent;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.utils.LogEventCaptor;
import ch.qos.logback.classic.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IOClientTest {

    @Mock private PnIoConnectorConfig config;
    @Mock private DefaultApi mockDefaultApi;
    @InjectMocks private IOClient ioClient;

    private static final String API_KEY = "test-api-key";

    @BeforeEach
    void setup() {
        @SuppressWarnings("unchecked")
        Map<String, DefaultApi> cache = (Map<String, DefaultApi>)
            ReflectionTestUtils.getField(ioClient, "defaultApiCache");
        cache.put(API_KEY, mockDefaultApi);
    }

    @Test
    void sendMessage_returnsIoMessageId() {
        var createdMessage = new CreatedMessage();
        createdMessage.setId("IO-MSG-001");
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any())).thenReturn(createdMessage);

        String result = ioClient.sendMessage(new NewMessage(), API_KEY);

        assertThat(result).isEqualTo("IO-MSG-001");
    }

    @Test
    void sendMessage_propagates_PnHttpResponseException_on404() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
            .thenThrow(new PnHttpResponseException("Not Found", 404));

        assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
            .isInstanceOf(PnHttpResponseException.class)
            .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(404));
    }

    @Test
    void sendMessage_propagates_PnHttpResponseException_on429() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
            .thenThrow(new PnHttpResponseException("Too Many Requests", 429));

        assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
            .isInstanceOf(PnHttpResponseException.class)
            .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(429));
    }

    @Test
    void sendMessage_propagates_PnHttpResponseException_on500() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
            .thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
            .isInstanceOf(PnHttpResponseException.class)
            .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(500));
    }

    @Test
    void sendMessage_propagates_PnHttpResponseException_on400() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
            .thenThrow(new PnHttpResponseException("Bad Request", 400));

        assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
            .isInstanceOf(PnHttpResponseException.class)
            .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(400));
    }

    @Test
    void getMessageStatus_returnsResponse() {
        var expected = new ExternalMessageResponseWithContent();
        when(mockDefaultApi.getMessage(eq("FISCAL_CODE"), eq("IO-MSG-001"))).thenReturn(expected);

        ExternalMessageResponseWithContent result = ioClient.getMessageStatus("FISCAL_CODE", "IO-MSG-001", API_KEY);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getMessageStatus_propagates_PnHttpResponseException_on404() {
        when(mockDefaultApi.getMessage(any(), any()))
            .thenThrow(new PnHttpResponseException("Not Found", 404));

        assertThatThrownBy(() -> ioClient.getMessageStatus("FISCAL_CODE", "IO-MSG-001", API_KEY))
            .isInstanceOf(PnHttpResponseException.class)
            .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(404));
    }

    @Test
    void getMessageStatus_propagates_PnHttpResponseException_on429() {
        when(mockDefaultApi.getMessage(any(), any()))
            .thenThrow(new PnHttpResponseException("Too Many Requests", 429));

        assertThatThrownBy(() -> ioClient.getMessageStatus("FISCAL_CODE", "IO-MSG-001", API_KEY))
            .isInstanceOf(PnHttpResponseException.class)
            .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(429));
    }

    @Test
    void getMessageStatus_propagates_PnHttpResponseException_on500() {
        when(mockDefaultApi.getMessage(any(), any()))
            .thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        assertThatThrownBy(() -> ioClient.getMessageStatus("FISCAL_CODE", "IO-MSG-001", API_KEY))
            .isInstanceOf(PnHttpResponseException.class)
            .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(500));
    }

    @Test
    void checkUserProfile_logsDownstreamInvocation() {
        when(mockDefaultApi.getProfileByPOST(any())).thenReturn(new LimitedProfile());

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            ioClient.checkUserProfile(new FiscalCodePayload(), API_KEY);

            assertThat(logs.hasEvent(Level.INFO, "[DOWNSTREAM] Invoking external service IO")).isTrue();
        }
    }

    @Test
    void sendMessage_logsDownstreamInvocation() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any())).thenReturn(new CreatedMessage().id("IO-MSG-001"));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            ioClient.sendMessage(new NewMessage(), API_KEY);

            assertThat(logs.hasEvent(Level.INFO, "[DOWNSTREAM] Invoking external service IO")).isTrue();
        }
    }

    @Test
    void getMessageStatus_logsDownstreamInvocation() {
        when(mockDefaultApi.getMessage(any(), any())).thenReturn(new ExternalMessageResponseWithContent());

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            ioClient.getMessageStatus("FISCAL_CODE", "IO-MSG-001", API_KEY);

            assertThat(logs.hasEvent(Level.INFO, "[DOWNSTREAM] Invoking external service IO")).isTrue();
        }
    }

    @Test
    void sendMessage_error_logsDownstreamFailed() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
                .thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
                    .isInstanceOf(PnHttpResponseException.class);

            assertThat(logs.hasEvent(Level.ERROR, "[DOWNSTREAM] Service IO returned errors=")).isTrue();
            assertThat(logs.events()).noneMatch(e -> e.getLevel() == Level.WARN);
        }
    }

    @Test
    void getMessageStatus_error_logsDownstreamFailed() {
        when(mockDefaultApi.getMessage(any(), any()))
                .thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            assertThatThrownBy(() -> ioClient.getMessageStatus("FISCAL_CODE", "IO-MSG-001", API_KEY))
                    .isInstanceOf(PnHttpResponseException.class);

            assertThat(logs.hasEvent(Level.ERROR, "[DOWNSTREAM] Service IO returned errors=")).isTrue();
            assertThat(logs.events()).noneMatch(e -> e.getLevel() == Level.WARN);
        }
    }

    private static final String DOWNSTREAM_RETURNED_ERRORS = "[DOWNSTREAM] Service IO returned errors=";
    private static final String REAL_TAX_ID = "RSSMRA80A01H501U";

    private static PnHttpResponseException realisticIoException(int status) {
        return new PnHttpResponseException("Error in call url=https://api.io.pagopa.it/api/v1/messages/"
                + REAL_TAX_ID + "/01JC7ZQ3M0 method=GET statusCode=" + status
                + " and body=<html>Bad Gateway</html>", status);
    }

    @Test
    void sendMessage_404_logsDownstreamFailedAsError() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
                    .isInstanceOf(PnHttpResponseException.class);

            assertThat(logs.hasEvent(Level.ERROR, DOWNSTREAM_RETURNED_ERRORS, "status=404")).isTrue();
            assertThat(logs.events()).noneMatch(e -> e.getLevel() == Level.INFO && e.getFormattedMessage().contains(DOWNSTREAM_RETURNED_ERRORS));
            assertThat(logs.events()).noneMatch(e -> e.getLevel() == Level.WARN);
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void getMessageStatus_404_logsDownstreamFailedAsError() {
        when(mockDefaultApi.getMessage(any(), any()))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            assertThatThrownBy(() -> ioClient.getMessageStatus("FISCAL_CODE", "IO-MSG-001", API_KEY))
                    .isInstanceOf(PnHttpResponseException.class);

            assertThat(logs.hasEvent(Level.ERROR, DOWNSTREAM_RETURNED_ERRORS, "status=404")).isTrue();
            assertThat(logs.events()).noneMatch(e -> e.getLevel() == Level.INFO && e.getFormattedMessage().contains(DOWNSTREAM_RETURNED_ERRORS));
            assertThat(logs.events()).noneMatch(e -> e.getLevel() == Level.WARN);
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void checkUserProfile_404_logsDownstreamNotFoundAsInfoAndRethrows() {
        when(mockDefaultApi.getProfileByPOST(any())).thenThrow(new PnHttpResponseException("Not Found", 404));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            assertThatThrownBy(() -> ioClient.checkUserProfile(new FiscalCodePayload(), API_KEY))
                    .isInstanceOf(PnHttpResponseException.class)
                    .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(404));

            assertThat(logs.hasEvent(Level.INFO, DOWNSTREAM_RETURNED_ERRORS, "status=404")).isTrue();
            assertThat(logs.events()).noneMatch(e -> e.getLevel() == Level.ERROR || e.getLevel() == Level.WARN);
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void checkUserProfile_500_logsDownstreamFailedAndRethrows() {
        when(mockDefaultApi.getProfileByPOST(any())).thenThrow(new PnHttpResponseException("Server Error", 500));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            assertThatThrownBy(() -> ioClient.checkUserProfile(new FiscalCodePayload(), API_KEY))
                    .isInstanceOf(PnHttpResponseException.class)
                    .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(500));

            assertThat(logs.hasEvent(Level.ERROR, DOWNSTREAM_RETURNED_ERRORS, "status=500")).isTrue();
        }
    }

    @Test
    void sendMessage_error_neverLogsExceptionMessageOrStack() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any())).thenThrow(realisticIoException(502));

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
                    .isInstanceOf(PnHttpResponseException.class);

            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void getMessageStatus_error_neverLogsExceptionMessageOrStack() {
        when(mockDefaultApi.getMessage(any(), any())).thenThrow(realisticIoException(502));

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            assertThatThrownBy(() -> ioClient.getMessageStatus(REAL_TAX_ID, "01JC7ZQ3M0", API_KEY))
                    .isInstanceOf(PnHttpResponseException.class);

            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void checkUserProfile_error_neverLogsExceptionMessageOrStack() {
        when(mockDefaultApi.getProfileByPOST(any())).thenThrow(realisticIoException(502));

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            assertThatThrownBy(() -> ioClient.checkUserProfile(new FiscalCodePayload().fiscalCode(REAL_TAX_ID), API_KEY))
                    .isInstanceOf(PnHttpResponseException.class);

            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void checkUserProfile_networkError_logsDownstreamFailedAndRethrows() {
        ResourceAccessException networkError = new ResourceAccessException("I/O error");
        when(mockDefaultApi.getProfileByPOST(any())).thenThrow(networkError);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            assertThatThrownBy(() -> ioClient.checkUserProfile(new FiscalCodePayload(), API_KEY))
                    .isSameAs(networkError);

            assertThat(logs.hasEvent(Level.ERROR, DOWNSTREAM_RETURNED_ERRORS)).isTrue();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void sendMessage_networkError_logsDownstreamFailedAndRethrows() {
        ResourceAccessException networkError = new ResourceAccessException("I/O error");
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any())).thenThrow(networkError);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(IOClient.class)) {
            assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
                    .isSameAs(networkError);

            assertThat(logs.hasEvent(Level.ERROR, DOWNSTREAM_RETURNED_ERRORS)).isTrue();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
        }
    }

    @Test
    void getMessageStatus_networkError_logsDownstreamFailedAndRethrowsMasked() {
        ResourceAccessException networkError = new ResourceAccessException("I/O error on GET request for \"https://api.io.pagopa.it/api/v1/messages/"
                + REAL_TAX_ID + "/01JC7ZQ3M0\": Read timed out");
        when(mockDefaultApi.getMessage(any(), any())).thenThrow(networkError);

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            assertThatThrownBy(() -> ioClient.getMessageStatus(REAL_TAX_ID, "01JC7ZQ3M0", API_KEY))
                    .isInstanceOf(PnInternalException.class)
                    .hasNoCause()
                    .hasMessageNotContaining(REAL_TAX_ID)
                    .satisfies(e -> assertThat(((PnInternalException) e).getProblem().getDetail())
                            .doesNotContain(REAL_TAX_ID)
                            .contains("/messages/***/"));

            assertThat(logs.hasEvent(Level.ERROR, DOWNSTREAM_RETURNED_ERRORS)).isTrue();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
            assertThat(logs.anyEventContains("Read timed out")).isFalse();
        }
    }

    @Test
    void getMessageStatus_httpError_rethrowsMaskedPnHttpResponseException() {
        when(mockDefaultApi.getMessage(any(), any())).thenThrow(realisticIoException(502));

        try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
            assertThatThrownBy(() -> ioClient.getMessageStatus(REAL_TAX_ID, "01JC7ZQ3M0", API_KEY))
                    .isInstanceOf(PnHttpResponseException.class)
                    .hasNoCause()
                    .hasMessageNotContaining(REAL_TAX_ID)
                    .hasMessageContaining("/messages/***/")
                    .satisfies(e -> assertThat(((PnHttpResponseException) e).getStatusCode()).isEqualTo(502));

            assertThat(logs.anyEventContains(REAL_TAX_ID)).isFalse();
            assertThat(logs.anyEventContains("Bad Gateway")).isFalse();
        }
    }

    @Test
    void checkUserProfile_httpError_rethrowsOriginalException() {
        PnHttpResponseException original = realisticIoException(502);
        when(mockDefaultApi.getProfileByPOST(any())).thenThrow(original);

        assertThatThrownBy(() -> ioClient.checkUserProfile(new FiscalCodePayload(), API_KEY)).isSameAs(original);
    }
}
