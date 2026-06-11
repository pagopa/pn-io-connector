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

import java.util.Map;

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
}
