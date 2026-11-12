package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.externalregistries.v1.api.PrivacyNoticeApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.externalregistries.v1.dto.PrivacyNoticeVersionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalRegistriesPrivacyNoticeClientTest {

    @Mock
    private PrivacyNoticeApi privacyNoticeApi;

    @InjectMocks
    private ExternalRegistriesPrivacyNoticeClient client;

    @Test
    void findPrivacyNoticeVersion_passesConsentsTypeAndPortalTypeToApi() {
        when(privacyNoticeApi.findPrivacyNoticeVersion("DATAPRIVACY", "PG"))
                .thenReturn(new PrivacyNoticeVersionResponse().version(12));

        Integer version = client.findPrivacyNoticeVersion("DATAPRIVACY", "PG");

        verify(privacyNoticeApi).findPrivacyNoticeVersion("DATAPRIVACY", "PG");
        assertThat(version).isEqualTo(12);
    }

    @Test
    void findPrivacyNoticeVersion_apiFails_propagatesError() {
        PnHttpResponseException error = new PnHttpResponseException("external-registries error", 500);
        when(privacyNoticeApi.findPrivacyNoticeVersion("TOS", "PF")).thenThrow(error);

        assertThatThrownBy(() -> client.findPrivacyNoticeVersion("TOS", "PF"))
                .isSameAs(error);
    }

    @Test
    void findPrivacyNoticeVersion_restClientFails_propagatesError() {
        RestClientException error = new RestClientException("connection refused");
        when(privacyNoticeApi.findPrivacyNoticeVersion("TOS", "PF")).thenThrow(error);

        assertThatThrownBy(() -> client.findPrivacyNoticeVersion("TOS", "PF"))
                .isSameAs(error);
    }
}
