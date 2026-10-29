package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.api.ConsentsApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.ConsentAction;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.ConsentType;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.CxTypeAuthFleet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAttributesConsentClientTest {

    @Mock
    private ConsentsApi consentsApi;

    @InjectMocks
    private UserAttributesConsentClient client;

    @Test
    void acceptConsent_callsConsentActionWithAcceptOnIoChannel() {
        client.acceptConsent("uid-1", "cx-1", CxTypeAuthFleet.PF, ConsentType.TOS, 3);

        ArgumentCaptor<ConsentAction> body = ArgumentCaptor.forClass(ConsentAction.class);
        verify(consentsApi).consentAction(org.mockito.ArgumentMatchers.eq("uid-1"),
                org.mockito.ArgumentMatchers.eq("cx-1"),
                org.mockito.ArgumentMatchers.eq(CxTypeAuthFleet.PF),
                org.mockito.ArgumentMatchers.eq(ConsentType.TOS),
                org.mockito.ArgumentMatchers.eq("3"),
                body.capture());
        assertThat(body.getValue().getAction()).isEqualTo(ConsentAction.ActionEnum.ACCEPT);
        assertThat(body.getValue().getChannel()).isEqualTo(ConsentAction.ChannelEnum.IO);
    }

    @Test
    void acceptConsent_passesHeadersConsentTypeAndVersionAsString() {
        client.acceptConsent("uid-2", "cx-2", CxTypeAuthFleet.PG, ConsentType.DATAPRIVACY, 12);

        verify(consentsApi).consentAction(org.mockito.ArgumentMatchers.eq("uid-2"),
                org.mockito.ArgumentMatchers.eq("cx-2"),
                org.mockito.ArgumentMatchers.eq(CxTypeAuthFleet.PG),
                org.mockito.ArgumentMatchers.eq(ConsentType.DATAPRIVACY),
                org.mockito.ArgumentMatchers.eq("12"),
                any(ConsentAction.class));
    }

    @Test
    void acceptConsent_apiFails_propagatesError() {
        PnHttpResponseException error = new PnHttpResponseException("user-attributes error", 500);
        doThrow(error).when(consentsApi).consentAction(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> client.acceptConsent("uid-1", "cx-1", CxTypeAuthFleet.PF, ConsentType.TOS, 3))
                .isSameAs(error);
    }

    @Test
    void acceptConsent_restClientFails_propagatesError() {
        RestClientException error = new RestClientException("connection refused");
        doThrow(error).when(consentsApi).consentAction(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> client.acceptConsent("uid-1", "cx-1", CxTypeAuthFleet.PF, ConsentType.TOS, 3))
                .isSameAs(error);
    }
}
