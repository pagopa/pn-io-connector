package it.pagopa.pn.ioconnector.middleware.msclient;

import ch.qos.logback.classic.Level;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.exceptions.PnDataVaultException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.api.RecipientsApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.dto.BaseRecipientDto;
import it.pagopa.pn.ioconnector.utils.LogEventCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataVaultClientTest {

    @Mock private RecipientsApi recipientsApi;
    @InjectMocks private DataVaultClient dataVaultClient;

    @Test
    void deanonymize_logsInvocation() {
        when(recipientsApi.getRecipientDenominationByInternalId(any())).thenReturn(List.of(new BaseRecipientDto()));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(DataVaultClient.class)) {
            dataVaultClient.deanonymize("PF-abc123");

            assertThat(logs.hasEvent(Level.INFO, "[DOWNSTREAM] Invoking external service pn-data-vault")).isTrue();
        }
    }

    @Test
    void deanonymize_error_logsDownstreamFailed() {
        when(recipientsApi.getRecipientDenominationByInternalId(any()))
                .thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(DataVaultClient.class)) {
            assertThatThrownBy(() -> dataVaultClient.deanonymize("PF-abc123"))
                    .isInstanceOf(PnDataVaultException.class);

            assertThat(logs.hasEvent(Level.ERROR, "[DOWNSTREAM] Service pn-data-vault returned errors=", "status=500")).isTrue();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
            assertThat(logs.anyMessageContains("Internal Server Error")).isFalse();
        }
    }

    @Test
    void deanonymize_networkError_logsDownstreamFailedAndRethrowsSameException() {
        ResourceAccessException networkError = new ResourceAccessException("I/O error on GET request: Read timed out");
        when(recipientsApi.getRecipientDenominationByInternalId(any())).thenThrow(networkError);

        try (LogEventCaptor logs = LogEventCaptor.attachTo(DataVaultClient.class)) {
            assertThatThrownBy(() -> dataVaultClient.deanonymize("PF-abc123"))
                    .isSameAs(networkError);

            assertThat(logs.hasEvent(Level.ERROR, "[DOWNSTREAM] Service pn-data-vault returned errors=",
                    "deanonymize failed - ResourceAccessException")).isTrue();
            assertThat(logs.events()).allMatch(e -> e.getThrowableProxy() == null);
            assertThat(logs.anyMessageContains("Read timed out")).isFalse();
        }
    }
}
