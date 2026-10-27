package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.NewMessage;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorOptInDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorOptInEntity;
import it.pagopa.pn.ioconnector.middleware.msclient.IOLegalClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendIoMessageServiceTest {

    private static final String TAX_ID = "RSSMRA80A01H501U";
    private static final String EXPECTED_HASHED_TAX_ID =
            "82e98709e2f96efd33bed69e81ab7e25e2f363dd804e4014c46f36b9805bff6e";
    private static final int COOLDOWN_DAYS = 30;

    private static final String IUN = "IUN-001";
    private static final String INTERNAL_ID = "internal-id-001";
    private static final int REC_INDEX = 0;
    private static final String IO_MESSAGE_ID = "io-message-id";

    @Mock
    private IOConnectorOptInDao ioConnectorOptInDAO;
    @Mock
    private IOLegalClient ioLegalClient;
    @Mock
    private IoLegalMessageBuilder ioLegalMessageBuilder;
    @Mock
    private SendIOSentMessageService sendIOSentMessageService;

    private PnIoConnectorConfig config;

    private SendIoMessageService sendIoMessageService;

    @BeforeEach
    void setUp() {
        config = new PnIoConnectorConfig();
        config.setIoOptinMinDays(COOLDOWN_DAYS);
        config.setEnableIoMessage(true);
        config.setEnableIoActivationMessage(true);
        sendIoMessageService = new SendIoMessageService(config, ioConnectorOptInDAO, ioLegalClient,
                ioLegalMessageBuilder, sendIOSentMessageService);
    }

    private SendMessageRequest buildRequest() {
        return new SendMessageRequest(
                IUN,
                TAX_ID,
                INTERNAL_ID,
                REC_INDEX,
                "Comune di Milano");
    }

    private IOConnectorOptInEntity recordWithLastModified(Instant lastModified) {
        return IOConnectorOptInEntity.builder()
                .pk(EXPECTED_HASHED_TAX_ID)
                .created(lastModified)
                .lastModified(lastModified)
                .ttl(lastModified.plus(COOLDOWN_DAYS, ChronoUnit.DAYS).getEpochSecond())
                .build();
    }

    private void givenProfile(boolean senderAllowed, List<String> preferredLanguages) {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenReturn(new LimitedProfile().senderAllowed(senderAllowed).preferredLanguages(preferredLanguages));
    }

    private void givenOptInMessageSent() {
        NewMessage optInMessage = new NewMessage();
        when(ioLegalMessageBuilder.buildOptInMessage(any())).thenReturn(optInMessage);
        when(ioLegalClient.sendOptInMessage(optInMessage)).thenReturn(IO_MESSAGE_ID);
    }

    private IOConnectorOptInEntity verifyOptInRecordSaved() {
        ArgumentCaptor<IOConnectorOptInEntity> captor = ArgumentCaptor.forClass(IOConnectorOptInEntity.class);
        verify(ioConnectorOptInDAO).save(captor.capture());
        return captor.getValue();
    }

    // --- manageOptIn ---

    @Test
    void manageOptIn_WhenRecordMissing_ThenSendsOptInAndSavesRecord() {
        when(ioConnectorOptInDAO.get(EXPECTED_HASHED_TAX_ID)).thenReturn(Optional.empty());
        givenOptInMessageSent();
        Instant before = Instant.now();

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.SENT_OPTIN);
        assertThat(result.getId()).isEqualTo(IO_MESSAGE_ID);
        IOConnectorOptInEntity saved = verifyOptInRecordSaved();
        assertThat(saved.getPk()).isEqualTo(EXPECTED_HASHED_TAX_ID);
        assertThat(saved.getLastModified()).isAfterOrEqualTo(before);
        assertThat(saved.getTtl()).isEqualTo(saved.getLastModified().plus(COOLDOWN_DAYS, ChronoUnit.DAYS).getEpochSecond());
    }

    @Test
    void manageOptIn_WhenLastSendIsOlderThanCooldown_ThenSendsOptInAndSavesRecord() {
        Instant lastModified = Instant.now().minus(COOLDOWN_DAYS + 1L, ChronoUnit.DAYS);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.of(recordWithLastModified(lastModified)));
        givenOptInMessageSent();

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.SENT_OPTIN);
        assertThat(result.getId()).isEqualTo(IO_MESSAGE_ID);
        assertThat(verifyOptInRecordSaved().getLastModified()).isAfter(lastModified);
    }

    @Test
    void manageOptIn_WhenLastSendIsInsideCooldown_ThenReturnsNotSentOptInAlreadySent() {
        Instant lastModified = Instant.now().minus(COOLDOWN_DAYS - 1L, ChronoUnit.DAYS);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.of(recordWithLastModified(lastModified)));

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_ALREADY_SENT);
        verify(ioLegalClient, never()).sendOptInMessage(any());
        verify(ioConnectorOptInDAO, never()).save(any());
    }

    @Test
    void manageOptIn_WhenCooldownIsZero_ThenAlwaysSendsOptIn() {
        config.setIoOptinMinDays(0);
        when(ioConnectorOptInDAO.get(anyString()))
                .thenReturn(Optional.of(recordWithLastModified(Instant.now().minusSeconds(60))));
        givenOptInMessageSent();

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.SENT_OPTIN);
        verify(ioConnectorOptInDAO).save(any());
    }

    @Test
    void manageOptIn_WhenIoSendFails_ThenReturnsErrorOptInAndSavesRecord() {
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        when(ioLegalMessageBuilder.buildOptInMessage(any())).thenReturn(new NewMessage());
        when(ioLegalClient.sendOptInMessage(any())).thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.ERROR_OPTIN);
        assertThat(result.getId()).isNull();
        verify(ioConnectorOptInDAO).save(any());
    }

    @Test
    void manageOptIn_WhenOptInDisabledByConf_ThenDoesNotSendButSavesRecord() {
        config.setEnableIoActivationMessage(false);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_DISABLED_BY_CONF);
        verify(ioLegalClient, never()).sendOptInMessage(any());
        verify(ioConnectorOptInDAO).save(any());
    }

    @Test
    void manageOptIn_WhenOptInDisabledByConfAndInsideCooldown_ThenReturnsAlreadySentWithoutSave() {
        config.setEnableIoActivationMessage(false);
        Instant lastModified = Instant.now().minus(1, ChronoUnit.DAYS);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.of(recordWithLastModified(lastModified)));

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_ALREADY_SENT);
        verify(ioConnectorOptInDAO, never()).save(any());
    }

    // --- sendMessage ---

    @Test
    void sendMessage_WhenAppIoNotActive_ThenReturnsNotSentAppIoUnavailable() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.NOT_SENT_APPIO_UNAVAILABLE);
        assertThat(result.getId()).isNull();
        verify(ioLegalClient, never()).sendCourtesyMessage(any());
        verify(ioLegalClient, never()).sendOptInMessage(any());
        verifyNoInteractions(ioConnectorOptInDAO, sendIOSentMessageService);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void sendMessage_WhenUserStatusError_ThenReturnsErrorUserStatus(int statusCode) {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("error", statusCode));

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.ERROR_USER_STATUS);
        verify(ioLegalClient, never()).sendCourtesyMessage(any());
        verifyNoInteractions(ioConnectorOptInDAO, sendIOSentMessageService);
    }

    @Test
    void sendMessage_WhenUserStatusNonHttpError_ThenIsPropagated() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new IllegalStateException("connection reset"));
        SendMessageRequest request = buildRequest();

        assertThrows(IllegalStateException.class, () -> sendIoMessageService.sendMessage(request));
        assertThat(MDC.get(MDCUtils.MDC_PN_IUN_KEY)).isNull();
    }

    @Test
    void sendMessage_WhenPnActiveAndCourtesyDisabledByConf_ThenReturnsNotSentCourtesyDisabled() {
        config.setEnableIoMessage(false);
        givenProfile(true, null);

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.NOT_SENT_COURTESY_DISABLED_BY_CONF);
        verify(ioLegalClient, never()).sendCourtesyMessage(any());
        verifyNoInteractions(ioLegalMessageBuilder, sendIOSentMessageService);
    }

    static Stream<Arguments> courtesyLocale() {
        return Stream.of(
                arguments(null, true),
                arguments(List.of(), true),
                arguments(List.of("en_GB"), false),
                arguments(List.of("en_GB", "it_IT"), true)
        );
    }

    @ParameterizedTest
    @MethodSource("courtesyLocale")
    void sendMessage_WhenPnActive_ThenSendsCourtesyWithPreferredLanguage(List<String> preferredLanguages, boolean expectedIT) {
        givenProfile(true, preferredLanguages);
        NewMessage courtesyMessage = new NewMessage();
        SendMessageRequest request = buildRequest();
        when(ioLegalMessageBuilder.buildCourtesyMessage(request, expectedIT)).thenReturn(courtesyMessage);
        when(ioLegalClient.sendCourtesyMessage(courtesyMessage)).thenReturn(IO_MESSAGE_ID);

        SendMessageResponse result = sendIoMessageService.sendMessage(request);

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.SENT_COURTESY);
        assertThat(result.getId()).isEqualTo(IO_MESSAGE_ID);
        verify(ioLegalMessageBuilder).buildCourtesyMessage(request, expectedIT);
        verifyNoInteractions(ioConnectorOptInDAO);
    }

    @Test
    void sendMessage_WhenCarbonCopyRequested_ThenNotifiesDeliveryPush() {
        givenProfile(true, null);
        when(ioLegalMessageBuilder.buildCourtesyMessage(any(), anyBoolean())).thenReturn(new NewMessage());
        when(ioLegalClient.sendCourtesyMessage(any())).thenReturn(IO_MESSAGE_ID);
        SendMessageRequest request = buildRequest().carbonCopyToDeliveryPush(true);
        Instant before = Instant.now();

        SendMessageResponse result = sendIoMessageService.sendMessage(request);

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.SENT_COURTESY);
        assertThat(result.getId()).isEqualTo(IO_MESSAGE_ID);
        ArgumentCaptor<Instant> sentDate = ArgumentCaptor.forClass(Instant.class);
        verify(sendIOSentMessageService).sendIOSentMessageNotification(eq(IUN), eq(REC_INDEX), eq(INTERNAL_ID), sentDate.capture());
        assertThat(sentDate.getValue()).isAfterOrEqualTo(before);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void sendMessage_WhenCarbonCopyNotRequested_ThenDoesNotNotifyDeliveryPush(Boolean carbonCopy) {
        givenProfile(true, null);
        when(ioLegalMessageBuilder.buildCourtesyMessage(any(), anyBoolean())).thenReturn(new NewMessage());
        when(ioLegalClient.sendCourtesyMessage(any())).thenReturn(IO_MESSAGE_ID);

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest().carbonCopyToDeliveryPush(carbonCopy));

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.SENT_COURTESY);
        verifyNoInteractions(sendIOSentMessageService);
    }

    @Test
    void sendMessage_WhenCourtesySendFails_ThenReturnsErrorCourtesyWithoutCarbonCopy() {
        givenProfile(true, null);
        when(ioLegalMessageBuilder.buildCourtesyMessage(any(), anyBoolean())).thenReturn(new NewMessage());
        when(ioLegalClient.sendCourtesyMessage(any())).thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest().carbonCopyToDeliveryPush(true));

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.ERROR_COURTESY);
        assertThat(result.getId()).isNull();
        verifyNoInteractions(sendIOSentMessageService);
    }

    @Test
    void sendMessage_WhenCarbonCopyFails_ThenReturnsErrorCourtesyWithoutId() {
        givenProfile(true, null);
        when(ioLegalMessageBuilder.buildCourtesyMessage(any(), anyBoolean())).thenReturn(new NewMessage());
        when(ioLegalClient.sendCourtesyMessage(any())).thenReturn(IO_MESSAGE_ID);
        doThrow(new RuntimeException("sqs down")).when(sendIOSentMessageService)
                .sendIOSentMessageNotification(anyString(), anyInt(), anyString(), any());

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest().carbonCopyToDeliveryPush(true));

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.ERROR_COURTESY);
        assertThat(result.getId()).isNull();
    }

    @Test
    void sendMessage_WhenPnNotActive_ThenSendsOptInWithoutRecursion() {
        givenProfile(false, List.of("it_IT"));
        when(ioConnectorOptInDAO.get(EXPECTED_HASHED_TAX_ID)).thenReturn(Optional.empty());
        givenOptInMessageSent();

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.SENT_OPTIN);
        assertThat(result.getId()).isEqualTo(IO_MESSAGE_ID);
        verify(ioLegalClient).getProfile(any(FiscalCodePayload.class));
        verify(ioLegalClient, never()).sendCourtesyMessage(any());
        verify(ioConnectorOptInDAO).save(any());
    }

    @Test
    void sendMessage_WhenRequestIdMissing_ThenGeneratesIt() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest());

        assertThat(result.getRequestId()).isNotBlank();
        assertThat(UUID.fromString(result.getRequestId())).isNotNull();
    }

    @Test
    void sendMessage_WhenRequestIdPresent_ThenReturnsItUnchanged() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        SendMessageResponse result = sendIoMessageService.sendMessage(buildRequest().requestId("req-123"));

        assertThat(result.getRequestId()).isEqualTo("req-123");
        assertThat(MDC.get(MDCUtils.MDC_PN_CTX_REQUEST_ID)).isNull();
    }

    // --- getUserStatus ---

    @Test
    void getUserStatus_pnActive() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenReturn(new LimitedProfile().senderAllowed(true));

        UserStatusResponse result = sendIoMessageService.getUserStatus(new UserStatusRequest(TAX_ID));

        assertThat(result.getTaxId()).isEqualTo(TAX_ID);
        assertThat(result.getStatus()).isEqualTo(UserStatusResponse.StatusEnum.PN_ACTIVE);
    }

    @Test
    void getUserStatus_pnNotActive() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenReturn(new LimitedProfile().senderAllowed(false));

        UserStatusResponse result = sendIoMessageService.getUserStatus(new UserStatusRequest(TAX_ID));

        assertThat(result.getTaxId()).isEqualTo(TAX_ID);
        assertThat(result.getStatus()).isEqualTo(UserStatusResponse.StatusEnum.PN_NOT_ACTIVE);
    }

    @Test
    void getUserStatus_appioNotActive() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        UserStatusResponse result = sendIoMessageService.getUserStatus(new UserStatusRequest(TAX_ID));

        assertThat(result.getTaxId()).isEqualTo(TAX_ID);
        assertThat(result.getStatus()).isEqualTo(UserStatusResponse.StatusEnum.APPIO_NOT_ACTIVE);
    }

    @Test
    void getUserStatus_error() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        UserStatusResponse result = sendIoMessageService.getUserStatus(new UserStatusRequest(TAX_ID));

        assertThat(result.getTaxId()).isEqualTo(TAX_ID);
        assertThat(result.getStatus()).isEqualTo(UserStatusResponse.StatusEnum.ERROR);
    }

    @Test
    void getUserStatusInternal_pnActive_returnsPreferredLanguages() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenReturn(new LimitedProfile().senderAllowed(true).preferredLanguages(List.of("en_GB", "it_IT")));

        UserStatusInternal result = sendIoMessageService.getUserStatusInternal(TAX_ID);

        assertThat(result.status()).isEqualTo(UserStatusResponse.StatusEnum.PN_ACTIVE);
        assertThat(result.preferredLanguages()).containsExactly("en_GB", "it_IT");
    }

    @Test
    void getUserStatusInternal_pnNotActive_returnsPreferredLanguages() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenReturn(new LimitedProfile().senderAllowed(false).preferredLanguages(List.of("en_GB")));

        UserStatusInternal result = sendIoMessageService.getUserStatusInternal(TAX_ID);

        assertThat(result.status()).isEqualTo(UserStatusResponse.StatusEnum.PN_NOT_ACTIVE);
        assertThat(result.preferredLanguages()).containsExactly("en_GB");
    }

    @Test
    void getUserStatusInternal_appioNotActive_hasNoPreferredLanguages() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("Not Found", 404));

        UserStatusInternal result = sendIoMessageService.getUserStatusInternal(TAX_ID);

        assertThat(result.status()).isEqualTo(UserStatusResponse.StatusEnum.APPIO_NOT_ACTIVE);
        assertThat(result.preferredLanguages()).isNull();
    }

    @Test
    void getUserStatusInternal_error_hasNoPreferredLanguages() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        UserStatusInternal result = sendIoMessageService.getUserStatusInternal(TAX_ID);

        assertThat(result.status()).isEqualTo(UserStatusResponse.StatusEnum.ERROR);
        assertThat(result.preferredLanguages()).isNull();
    }

    @Test
    void getUserStatus_nonHttpError_isPropagated() {
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenThrow(new IllegalStateException("connection reset"));
        UserStatusRequest request = new UserStatusRequest(TAX_ID);

        assertThrows(IllegalStateException.class,
                () -> sendIoMessageService.getUserStatus(request));
    }

    static Stream<Arguments> preferredLanguages() {
        return Stream.of(
                arguments(null, true),
                arguments(List.of(), true),
                arguments(List.of("it_IT"), true),
                arguments(List.of("en_GB"), false),
                arguments(List.of("en_GB", "it_IT"), true),
                arguments(List.of("de_DE", "fr_FR"), false)
        );
    }

    @ParameterizedTest
    @MethodSource("preferredLanguages")
    void isPreferredLanguageIT(List<String> preferredLanguages, boolean expected) {
        assertThat(SendIoMessageService.isPreferredLanguageIT(preferredLanguages)).isEqualTo(expected);
    }
}
