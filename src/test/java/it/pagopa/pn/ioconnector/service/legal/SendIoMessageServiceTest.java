package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.exceptions.PnNotImplementedException;
import it.pagopa.pn.ioconnector.config.PnIoConnectorConfig;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendIoMessageServiceTest {

    private static final String TAX_ID = "RSSMRA80A01H501U";
    private static final String EXPECTED_HASHED_TAX_ID =
            "82e98709e2f96efd33bed69e81ab7e25e2f363dd804e4014c46f36b9805bff6e";
    private static final int COOLDOWN_DAYS = 30;

    @Mock
    private IOConnectorOptInDao ioConnectorOptInDAO;
    @Mock
    private IOLegalClient ioLegalClient;

    private PnIoConnectorConfig config;
    private SendMessageResponse sentResponse;

    private SendIoMessageService sendIoMessageService;

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @BeforeEach
    void setUp() {
        config = new PnIoConnectorConfig();
        config.setIoOptinMinDays(COOLDOWN_DAYS);
        sendIoMessageService = spy(new SendIoMessageService(config, ioConnectorOptInDAO, ioLegalClient));

        sentResponse = new SendMessageResponse();
        sentResponse.setResult(SendMessageResponse.ResultEnum.SENT_OPTIN);
    }

    private SendMessageRequest buildRequest() {
        return new SendMessageRequest(
                "IUN-001",
                TAX_ID,
                "internal-id-001",
                0,
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

    @Test
    void manageOptIn_WhenRecordMissing_ThenDelegatesToSendMessage() {
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        doReturn(sentResponse).when(sendIoMessageService).sendMessage(any());
        SendMessageRequest request = buildRequest();

        SendMessageResponse result = sendIoMessageService.manageOptIn(request);

        assertThat(result).isSameAs(sentResponse);
        verify(sendIoMessageService).sendMessage(request);
    }

    @Test
    void manageOptIn_WhenLastSendIsOlderThanCooldown_ThenDelegatesToSendMessage() {
        Instant lastModified = Instant.now().minus(COOLDOWN_DAYS + 1L, ChronoUnit.DAYS);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.of(recordWithLastModified(lastModified)));
        doReturn(sentResponse).when(sendIoMessageService).sendMessage(any());
        SendMessageRequest request = buildRequest();

        SendMessageResponse result = sendIoMessageService.manageOptIn(request);

        assertThat(result).isSameAs(sentResponse);
        verify(sendIoMessageService).sendMessage(request);
    }

    @Test
    void manageOptIn_WhenLastSendIsInsideCooldown_ThenReturnsNotSentOptInAlreadySent() {
        Instant lastModified = Instant.now().minus(COOLDOWN_DAYS - 1L, ChronoUnit.DAYS);
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.of(recordWithLastModified(lastModified)));

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result.getResult()).isEqualTo(SendMessageResponse.ResultEnum.NOT_SENT_OPTIN_ALREADY_SENT);
        verify(sendIoMessageService, never()).sendMessage(any());
    }

    @Test
    void manageOptIn_WhenCooldownIsZero_ThenAlwaysDelegatesToSendMessage() {
        config.setIoOptinMinDays(0);
        when(ioConnectorOptInDAO.get(anyString()))
                .thenReturn(Optional.of(recordWithLastModified(Instant.now().minusSeconds(60))));
        doReturn(sentResponse).when(sendIoMessageService).sendMessage(any());

        SendMessageResponse result = sendIoMessageService.manageOptIn(buildRequest());

        assertThat(result).isSameAs(sentResponse);
    }

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
    void manageOptIn_setsMdcRequestIdIunAndInternalIdNotTaxId() {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        when(ioConnectorOptInDAO.get(anyString()))
                .thenAnswer(MdcTestSupport.capturingInto(captured, Optional.<IOConnectorOptInEntity>empty()));
        doReturn(sentResponse).when(sendIoMessageService).sendMessage(any());
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
        doReturn(sentResponse).when(sendIoMessageService).sendMessage(any());

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
        doReturn(sentResponse).when(sendIoMessageService).sendMessage(any());

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
    void manageOptIn_cooldownExpiredAndSendMessageThrows_logsSingleEndingProcessWithErrors() {
        when(ioConnectorOptInDAO.get(anyString())).thenReturn(Optional.empty());
        SendMessageRequest request = buildRequest().requestId("REQ-001");

        try (LogEventCaptor logs = LogEventCaptor.attachTo(SendIoMessageService.class)) {
            assertThatThrownBy(() -> sendIoMessageService.manageOptIn(request))
                    .isInstanceOf(PnNotImplementedException.class);

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
