package it.pagopa.pn.ioconnector.middleware.msclient;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.config.IoWhitelistChecker;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.api.DefaultApi;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.CreatedMessage;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IOLegalClientTest {

    private static final String ALLOWED_CF = "RSSMRA80A01H501U";
    private static final String BLOCKED_CF = "BNCGNN90C03L219Z";

    @Mock
    private DefaultApi ioLegalApi;

    @Mock
    private DefaultApi ioOptInApi;

    private IOLegalClient clientWithWhitelist() {
        return new IOLegalClient(ioLegalApi, ioOptInApi, new IoWhitelistChecker(ALLOWED_CF));
    }

    private IOLegalClient clientWithoutWhitelist() {
        return new IOLegalClient(ioLegalApi, ioOptInApi, new IoWhitelistChecker("*"));
    }

    private static FiscalCodePayload fiscalCode(String cf) {
        return new FiscalCodePayload().fiscalCode(cf);
    }

    private static ActivationPayload activationPayload(String cf) {
        return new ActivationPayload().fiscalCode(cf).status("ACTIVE");
    }

    private static NewMessage newMessage(String cf) {
        return new NewMessage().fiscalCode(cf);
    }

    @Nested
    class KeyBinding {

        @Test
        void getProfile_usesLegalApi() {
            LimitedProfile expected = new LimitedProfile();
            when(ioLegalApi.getProfileByPOST(any())).thenReturn(expected);

            assertSame(expected, clientWithoutWhitelist().getProfile(fiscalCode(ALLOWED_CF)));

            verify(ioLegalApi).getProfileByPOST(any());
            verifyNoInteractions(ioOptInApi);
        }

        @Test
        void getActivation_usesLegalApi() {
            Activation expected = new Activation();
            when(ioLegalApi.getServiceActivationByPOST(any())).thenReturn(expected);

            assertSame(expected, clientWithoutWhitelist().getActivation(fiscalCode(ALLOWED_CF)));

            verify(ioLegalApi).getServiceActivationByPOST(any());
            verifyNoInteractions(ioOptInApi);
        }

        @Test
        void upsertActivation_usesLegalApi() {
            Activation expected = new Activation();
            when(ioLegalApi.upsertServiceActivation(any())).thenReturn(expected);

            assertSame(expected, clientWithoutWhitelist().upsertActivation(activationPayload(ALLOWED_CF)));

            verify(ioLegalApi).upsertServiceActivation(any());
            verifyNoInteractions(ioOptInApi);
        }

        @Test
        void sendCourtesyMessage_usesLegalApi() {
            when(ioLegalApi.submitMessageforUserWithFiscalCodeInBody(any()))
                    .thenReturn(new CreatedMessage().id("courtesy-id"));

            assertEquals("courtesy-id", clientWithoutWhitelist().sendCourtesyMessage(newMessage(ALLOWED_CF)));

            verify(ioLegalApi).submitMessageforUserWithFiscalCodeInBody(any());
            verifyNoInteractions(ioOptInApi);
        }

        @Test
        void sendOptInMessage_usesOptInApi() {
            when(ioOptInApi.submitMessageforUserWithFiscalCodeInBody(any()))
                    .thenReturn(new CreatedMessage().id("optin-id"));

            assertEquals("optin-id", clientWithoutWhitelist().sendOptInMessage(newMessage(ALLOWED_CF)));

            verify(ioOptInApi).submitMessageforUserWithFiscalCodeInBody(any());
            verifyNoInteractions(ioLegalApi);
        }
    }


    @Nested
    class WhitelistBlocked {

        @Test
        void getProfile_behavesLikeAnIo404() {
            IOLegalClient client = clientWithWhitelist();

            PnHttpResponseException ex = assertThrows(PnHttpResponseException.class,
                    () -> client.getProfile(fiscalCode(BLOCKED_CF)));

            assertEquals(404, ex.getStatusCode());
            verifyNoInteractions(ioLegalApi, ioOptInApi);
        }

        @Test
        void getActivation_returnsSimulatedInactive() {
            Activation activation = clientWithWhitelist().getActivation(fiscalCode(BLOCKED_CF));

            assertEquals("INACTIVE", activation.getStatus());
            assertEquals(BLOCKED_CF, activation.getFiscalCode());
            verify(ioLegalApi, never()).getServiceActivationByPOST(any());
            verifyNoInteractions(ioOptInApi);
        }

        @Test
        void upsertActivation_returnsSimulatedInactiveWithoutCallingIo() {
            Activation activation = clientWithWhitelist().upsertActivation(activationPayload(BLOCKED_CF));

            assertEquals("INACTIVE", activation.getStatus());
            verify(ioLegalApi, never()).upsertServiceActivation(any());
            verifyNoInteractions(ioOptInApi);
        }

        @Test
        void sendCourtesyMessage_returnsGeneratedId() {
            String id = clientWithWhitelist().sendCourtesyMessage(newMessage(BLOCKED_CF));

            assertNotNull(id);
            verify(ioLegalApi, never()).submitMessageforUserWithFiscalCodeInBody(any());
        }

        @Test
        void sendOptInMessage_returnsGeneratedId() {
            String id = clientWithWhitelist().sendOptInMessage(newMessage(BLOCKED_CF));

            assertNotNull(id);
            verify(ioOptInApi, never()).submitMessageforUserWithFiscalCodeInBody(any());
        }
    }

    @Nested
    class WhitelistAllowed {

        @Test
        void listedTaxId_reachesIo() {
            Activation expected = new Activation();
            when(ioLegalApi.upsertServiceActivation(any())).thenReturn(expected);

            assertSame(expected, clientWithWhitelist().upsertActivation(activationPayload(ALLOWED_CF)));

            verify(ioLegalApi).upsertServiceActivation(any());
        }
    }

}
