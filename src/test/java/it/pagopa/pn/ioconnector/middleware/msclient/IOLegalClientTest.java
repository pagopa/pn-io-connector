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
import it.pagopa.pn.ioconnector.utils.LogEventCaptor;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Nested
    class DownstreamLogging {

        private static final String INVOKING = "[DOWNSTREAM] Invoking external service IO";
        private static final String FAILED = "[DOWNSTREAM] Service IO returned errors=";

        @Test
        void getProfile_logsDownstreamInvocation() {
            when(ioLegalApi.getProfileByPOST(any())).thenReturn(new LimitedProfile());

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                clientWithoutWhitelist().getProfile(fiscalCode(ALLOWED_CF));

                assertTrue(logs.hasEvent(Level.INFO, INVOKING));
            }
        }

        @Test
        void getActivation_logsDownstreamInvocation() {
            when(ioLegalApi.getServiceActivationByPOST(any())).thenReturn(new Activation());

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                clientWithoutWhitelist().getActivation(fiscalCode(ALLOWED_CF));

                assertTrue(logs.hasEvent(Level.INFO, INVOKING));
            }
        }

        @Test
        void upsertActivation_logsDownstreamInvocation() {
            when(ioLegalApi.upsertServiceActivation(any())).thenReturn(new Activation());

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                clientWithoutWhitelist().upsertActivation(activationPayload(ALLOWED_CF));

                assertTrue(logs.hasEvent(Level.INFO, INVOKING));
            }
        }

        @Test
        void sendCourtesyMessage_logsDownstreamInvocation() {
            when(ioLegalApi.submitMessageforUserWithFiscalCodeInBody(any())).thenReturn(new CreatedMessage().id("courtesy-id"));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                clientWithoutWhitelist().sendCourtesyMessage(newMessage(ALLOWED_CF));

                assertTrue(logs.hasEvent(Level.INFO, INVOKING));
            }
        }

        @Test
        void sendOptInMessage_logsDownstreamInvocation() {
            when(ioOptInApi.submitMessageforUserWithFiscalCodeInBody(any())).thenReturn(new CreatedMessage().id("optin-id"));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                clientWithoutWhitelist().sendOptInMessage(newMessage(ALLOWED_CF));

                assertTrue(logs.hasEvent(Level.INFO, INVOKING));
            }
        }

        @Test
        void getProfile_error_logsDownstreamFailed() {
            when(ioLegalApi.getProfileByPOST(any())).thenThrow(new PnHttpResponseException("Server Error", 500));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class, () -> clientWithoutWhitelist().getProfile(fiscalCode(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.ERROR, FAILED));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.WARN));
            }
        }

        @Test
        void getActivation_error_logsDownstreamFailed() {
            when(ioLegalApi.getServiceActivationByPOST(any())).thenThrow(new PnHttpResponseException("Server Error", 500));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class, () -> clientWithoutWhitelist().getActivation(fiscalCode(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.ERROR, FAILED));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.WARN));
            }
        }

        @Test
        void upsertActivation_error_logsDownstreamFailed() {
            when(ioLegalApi.upsertServiceActivation(any())).thenThrow(new PnHttpResponseException("Too Many Requests", 429));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class,
                        () -> clientWithoutWhitelist().upsertActivation(activationPayload(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.ERROR, FAILED));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.WARN));
            }
        }

        @Test
        void sendCourtesyMessage_error_logsDownstreamFailed() {
            when(ioLegalApi.submitMessageforUserWithFiscalCodeInBody(any()))
                    .thenThrow(new PnHttpResponseException("Server Error", 500));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class,
                        () -> clientWithoutWhitelist().sendCourtesyMessage(newMessage(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.ERROR, FAILED));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.WARN));
            }
        }

        @Test
        void sendOptInMessage_error_logsDownstreamFailed() {
            when(ioOptInApi.submitMessageforUserWithFiscalCodeInBody(any()))
                    .thenThrow(new PnHttpResponseException("Server Error", 500));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class,
                        () -> clientWithoutWhitelist().sendOptInMessage(newMessage(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.ERROR, FAILED));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.WARN));
            }
        }

        @Test
        void sendOptInMessage_404_logsDownstreamFailedAsError() {
            when(ioOptInApi.submitMessageforUserWithFiscalCodeInBody(any()))
                    .thenThrow(new PnHttpResponseException("Not Found", 404));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class,
                        () -> clientWithoutWhitelist().sendOptInMessage(newMessage(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.ERROR, FAILED, "status=404"));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.INFO && e.getFormattedMessage().contains(FAILED)));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.WARN));
                assertTrue(logs.events().stream().allMatch(e -> e.getThrowableProxy() == null));
            }
        }

        @Test
        void sendCourtesyMessage_404_logsDownstreamFailedAsError() {
            when(ioLegalApi.submitMessageforUserWithFiscalCodeInBody(any()))
                    .thenThrow(new PnHttpResponseException("Not Found", 404));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class,
                        () -> clientWithoutWhitelist().sendCourtesyMessage(newMessage(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.ERROR, FAILED, "status=404"));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.INFO && e.getFormattedMessage().contains(FAILED)));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.WARN));
                assertTrue(logs.events().stream().allMatch(e -> e.getThrowableProxy() == null));
            }
        }

        @Test
        void upsertActivation_404_logsDownstreamFailedAsError() {
            when(ioLegalApi.upsertServiceActivation(any())).thenThrow(new PnHttpResponseException("Not Found", 404));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class,
                        () -> clientWithoutWhitelist().upsertActivation(activationPayload(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.ERROR, FAILED, "status=404"));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.INFO && e.getFormattedMessage().contains(FAILED)));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.WARN));
                assertTrue(logs.events().stream().allMatch(e -> e.getThrowableProxy() == null));
            }
        }

        @Test
        void getActivation_404_logsDownstreamNotFoundAsInfo() {
            when(ioLegalApi.getServiceActivationByPOST(any())).thenThrow(new PnHttpResponseException("Not Found", 404));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class, () -> clientWithoutWhitelist().getActivation(fiscalCode(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.INFO, FAILED, "status=404"));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.ERROR || e.getLevel() == Level.WARN));
                assertTrue(logs.events().stream().allMatch(e -> e.getThrowableProxy() == null));
            }
        }

        @Test
        void getProfile_404_logsDownstreamNotFoundAsInfo() {
            when(ioLegalApi.getProfileByPOST(any())).thenThrow(new PnHttpResponseException("Not Found", 404));

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                assertThrows(PnHttpResponseException.class, () -> clientWithoutWhitelist().getProfile(fiscalCode(ALLOWED_CF)));

                assertTrue(logs.hasEvent(Level.INFO, FAILED, "status=404"));
                assertTrue(logs.events().stream().noneMatch(e -> e.getLevel() == Level.ERROR || e.getLevel() == Level.WARN));
                assertTrue(logs.events().stream().allMatch(e -> e.getThrowableProxy() == null));
            }
        }

        @Test
        void invoke_networkError_logsDownstreamFailedAndRethrows() {
            ResourceAccessException networkError = new ResourceAccessException("I/O error");
            when(ioLegalApi.getProfileByPOST(any())).thenThrow(networkError);

            try (LogEventCaptor logs = LogEventCaptor.attachTo(IOLegalClient.class)) {
                ResourceAccessException thrown = assertThrows(ResourceAccessException.class,
                        () -> clientWithoutWhitelist().getProfile(fiscalCode(ALLOWED_CF)));

                assertSame(networkError, thrown);
                assertTrue(logs.hasEvent(Level.ERROR, FAILED));
                assertTrue(logs.events().stream().allMatch(e -> e.getThrowableProxy() == null));
            }
        }

        @Test
        void invoke_error_neverLogsExceptionMessageOrStack() {
            when(ioLegalApi.getServiceActivationByPOST(any())).thenThrow(new PnHttpResponseException(
                    "Error in call url=https://api.io.pagopa.it/api/v1/activations/" + ALLOWED_CF
                            + " method=POST statusCode=502 and body=<html>Bad Gateway</html>", 502));

            try (LogEventCaptor logs = LogEventCaptor.attachToAllApplicationLoggers()) {
                assertThrows(PnHttpResponseException.class, () -> clientWithoutWhitelist().getActivation(fiscalCode(ALLOWED_CF)));

                assertFalse(logs.anyEventContains(ALLOWED_CF));
                assertTrue(logs.events().stream().allMatch(e -> e.getThrowableProxy() == null));
            }
        }
    }
}
