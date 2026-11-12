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
import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.utils.LogEventCaptor;
import it.pagopa.pn.ioconnector.utils.MdcTestSupport;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

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
    void manageOptIn_WhenIoSendFails_ThenReturnsErrorOptInWithoutSavingRecord() {
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        when(ioLegalMessageBuilder.buildOptInMessage(any())).thenReturn(new NewMessage());
        when(ioLegalClient.sendOptInMessage(any())).thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.ERROR_OPTIN);
        assertThat(result.getId()).isNull();
        verify(ioConnectorOptInDAO, never()).save(any());
    }

    @Test
    void manageOptIn_WhenLastSendIsOlderThanCooldownAndIoSendFails_ThenDoesNotRefreshRecord() {
        Instant lastModified = Instant.now().minus(COOLDOWN_DAYS + 1L, ChronoUnit.DAYS);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.of(recordWithLastModified(lastModified)));
        when(ioLegalMessageBuilder.buildOptInMessage(any())).thenReturn(new NewMessage());
        when(ioLegalClient.sendOptInMessage(any())).thenThrow(new PnHttpResponseException("Internal Server Error", 500));

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.ERROR_OPTIN);
        assertThat(result.getId()).isNull();
        verify(ioConnectorOptInDAO, never()).save(any());
    }

    @Test
    void manageOptIn_WhenPreviousAttemptFailed_ThenNextAttemptSendsOptIn() {
        NewMessage optInMessage = new NewMessage();
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        when(ioLegalMessageBuilder.buildOptInMessage(any())).thenReturn(optInMessage);
        when(ioLegalClient.sendOptInMessage(optInMessage))
                .thenThrow(new PnHttpResponseException("Internal Server Error", 500))
                .thenReturn(IO_MESSAGE_ID);

        SendMessageResponse first = sendIoMessageService.manageOptIn(buildRequest());
        assertThat(first.getResult()).isEqualTo(SendMessageResponse.ResultEnum.ERROR_OPTIN);
        verify(ioConnectorOptInDAO, never()).save(any());

        SendMessageResponse second = sendIoMessageService.manageOptIn(buildRequest());
        assertThat(second.getResult()).isEqualTo(SendMessageResponse.ResultEnum.SENT_OPTIN);
        assertThat(second.getId()).isEqualTo(IO_MESSAGE_ID);
        verify(ioConnectorOptInDAO, times(1)).save(any());
        verify(ioLegalClient, times(2)).sendOptInMessage(optInMessage);
    }

    @Test
    void manageOptIn_WhenOptInDisabledByConf_ThenDoesNotSendAndDoesNotSaveRecord() {
        config.setEnableIoActivationMessage(false);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_DISABLED_BY_CONF);
        verify(ioLegalClient, never()).sendOptInMessage(any());
        verify(ioConnectorOptInDAO, never()).save(any());
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

    @Test
    void sendMessage_WhenCourtesyMessageBuildFails_ThenIsPropagated() {
        givenProfile(true, null);
        when(ioLegalMessageBuilder.buildCourtesyMessage(any(), anyBoolean())).thenThrow(new IllegalStateException("build error"));
        SendMessageRequest request = buildRequest().carbonCopyToDeliveryPush(true);

        assertThrows(IllegalStateException.class, () -> sendIoMessageService.sendMessage(request));
        verify(ioLegalClient, never()).sendCourtesyMessage(any());
        verifyNoInteractions(sendIOSentMessageService);
    }

    @Test
    void sendMessage_WhenOptInMessageBuildFails_ThenIsPropagatedWithoutSave() {
        givenProfile(false, null);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        when(ioLegalMessageBuilder.buildOptInMessage(any())).thenThrow(new IllegalStateException("build error"));
        SendMessageRequest request = buildRequest();

        assertThrows(IllegalStateException.class, () -> sendIoMessageService.sendMessage(request));
        verify(ioLegalClient, never()).sendOptInMessage(any());
        verify(ioConnectorOptInDAO, never()).save(any());
    }

    @Test
    void sendMessage_SetsMdcDuringProcessingAndClearsItAfter() {
        Map<String, String> mdcDuringCall = new HashMap<>();
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class))).thenAnswer(invocation -> {
            mdcDuringCall.putAll(MDC.getCopyOfContextMap());
            throw new PnHttpResponseException("Not Found", 404);
        });

        sendIoMessageService.sendMessage(buildRequest().requestId("req-123"));

        assertThat(mdcDuringCall)
                .containsEntry(MDCUtils.MDC_PN_IUN_KEY, IUN)
                .containsEntry(MDCUtils.MDC_CX_ID_KEY, INTERNAL_ID)
                .containsEntry(MDCUtils.MDC_PN_CTX_RECIPIENT_INDEX, String.valueOf(REC_INDEX))
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "req-123")
                .containsEntry(MDCUtils.MDC_PN_CTX_TOPIC, "sendIOMessage");
        assertThat(MDC.get(MDCUtils.MDC_PN_CTX_TOPIC)).isNull();
        assertThat(MDC.get(MDCUtils.MDC_PN_IUN_KEY)).isNull();
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

    @Test
    void manageOptIn_setsMdcRequestIdIunAndInternalIdNotTaxId() {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        when(ioConnectorOptInDAO.get(anyString()))
                .thenAnswer(MdcTestSupport.capturingInto(captured, Optional.<IOConnectorOptInEntity>empty()));
        givenOptInMessageSent();
        SendMessageRequest request = buildRequest().requestId("REQ-001");

        sendIoMessageService.manageOptIn(request);

        assertThat(captured.get())
                .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-001")
                .containsEntry(MDCUtils.MDC_PN_IUN_KEY, "IUN-001")
                .containsEntry(MDCUtils.MDC_CX_ID_KEY, "internal-id-001");
        assertThat(captured.get().values()).doesNotContain(TAX_ID);
    }

    @Test
    void manageOptIn_startingProcessLogCarriesDomainMdc() {
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        givenOptInMessageSent();

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendIoMessageService.class)) {
            sendIoMessageService.manageOptIn(buildRequest().requestId("REQ-001"));

            assertThat(logs.mdcAt("Starting process"))
                    .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-001")
                    .containsEntry(MDCUtils.MDC_PN_IUN_KEY, "IUN-001")
                    .containsEntry(MDCUtils.MDC_CX_ID_KEY, "internal-id-001");
        }
    }

    @Test
    void manageOptIn_restoresMdcAtEnd() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");
        MDC.put(MDCUtils.MDC_CX_ID_KEY, "header-cx-id");
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        givenOptInMessageSent();

        sendIoMessageService.manageOptIn(buildRequest().requestId("REQ-001"));

        assertThat(MdcTestSupport.snapshot())
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace")
                .containsEntry(MDCUtils.MDC_CX_ID_KEY, "header-cx-id")
                .doesNotContainKeys(MDCUtils.MDC_PN_CTX_REQUEST_ID, MDCUtils.MDC_PN_IUN_KEY);
    }

    @Test
    void manageOptIn_restoresMdcOnException() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");
        when(ioConnectorOptInDAO.get(anyString())).thenThrow(new IllegalStateException("dao down"));
        SendMessageRequest request = buildRequest().requestId("REQ-001");

        assertThatThrownBy(() -> sendIoMessageService.manageOptIn(request)).isInstanceOf(IllegalStateException.class);

        assertThat(MdcTestSupport.snapshot())
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace")
                .doesNotContainKeys(MDCUtils.MDC_PN_CTX_REQUEST_ID, MDCUtils.MDC_PN_IUN_KEY, MDCUtils.MDC_CX_ID_KEY);
    }

    @Test
    void manageOptIn_daoError_logsEndingProcessWithErrorsAndRethrows() {
        IllegalStateException failure = new IllegalStateException("dao down");
        when(ioConnectorOptInDAO.get(anyString())).thenThrow(failure);
        SendMessageRequest request = buildRequest().requestId("REQ-001");

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendIoMessageService.class)) {
            assertThatThrownBy(() -> sendIoMessageService.manageOptIn(request)).isSameAs(failure);

            assertThat(logs.hasEvent(Level.WARN, "Ending process manageOptIn with errors=", "IllegalStateException")).isTrue();
        }
    }

    @Test
    void manageOptIn_cooldownExpiredAndSaveThrows_logsSingleEndingProcessWithErrors() {
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        givenOptInMessageSent();
        doThrow(new IllegalStateException("dao down")).when(ioConnectorOptInDAO).save(any());
        SendMessageRequest request = buildRequest().requestId("REQ-001");

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendIoMessageService.class)) {
            assertThatThrownBy(() -> sendIoMessageService.manageOptIn(request))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(logs.countEvents(Level.INFO, "Ending process manageOptIn")).isZero();
            assertThat(logs.countEvents(Level.WARN, "Ending process manageOptIn with errors=")).isEqualTo(1);
            assertThat(logs.events().stream().filter(e -> e.getFormattedMessage().contains("Ending process manageOptIn"))).hasSize(1);
        }
    }

    @Test
    void getUserStatus_neverPutsTaxIdInMdcAndKeepsContext() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        when(ioLegalClient.getProfile(any(FiscalCodePayload.class)))
                .thenAnswer(MdcTestSupport.capturingInto(captured, new LimitedProfile().senderAllowed(true)));

        sendIoMessageService.getUserStatus(new UserStatusRequest(TAX_ID));

        assertThat(captured.get()).containsEntry(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");
        assertThat(captured.get().values()).doesNotContain(TAX_ID);
        assertThat(MdcTestSupport.snapshot()).containsOnlyKeys(MDCUtils.MDC_TRACE_ID_KEY);
    }
}
