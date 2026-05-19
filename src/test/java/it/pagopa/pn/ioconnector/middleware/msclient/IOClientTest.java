package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.DefaultApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.ManageAuthorizationApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.CreatedMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IOClientTest {

    @Mock private PnIoConnectorConfig config;
    @Mock private ManageAuthorizationApi manageAuthorizationApi;
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
    void sendMessage_throws_on404() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
            .thenThrow(new RestClientResponseException("Not Found", 404, "Not Found", null, null, null));

        assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
            .isInstanceOf(PnInternalException.class)
            .satisfies(e -> assertThat(((PnInternalException) e).getProblem().getErrors().get(0).getCode())
                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RECIPIENT_NOT_FOUND));
    }

    @Test
    void sendMessage_throws_on429() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
            .thenThrow(new RestClientResponseException("Too Many Requests", 429, "Too Many Requests", null, null, null));

        assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
            .isInstanceOf(PnInternalException.class)
            .satisfies(e -> assertThat(((PnInternalException) e).getProblem().getErrors().get(0).getCode())
                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_RATE_LIMIT));
    }

    @Test
    void sendMessage_throws_on500() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
            .thenThrow(new RestClientResponseException("Internal Server Error", 500, "Internal Server Error", null, null, null));

        assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
            .isInstanceOf(PnInternalException.class)
            .satisfies(e -> assertThat(((PnInternalException) e).getProblem().getErrors().get(0).getCode())
                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR));
    }

    @Test
    void sendMessage_throws_on400() {
        when(mockDefaultApi.submitMessageforUserWithFiscalCodeInBody(any()))
            .thenThrow(new RestClientResponseException("Bad Request", 400, "Bad Request", null, null, null));

        assertThatThrownBy(() -> ioClient.sendMessage(new NewMessage(), API_KEY))
            .isInstanceOf(PnInternalException.class)
            .satisfies(e -> assertThat(((PnInternalException) e).getProblem().getErrors().get(0).getCode())
                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_IO_SERVER_ERROR));
    }
}
