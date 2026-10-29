package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.ConsentType;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationStatus;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.ioconnector.middleware.msclient.IOLegalClient;
import it.pagopa.pn.ioconnector.middleware.msclient.UserAttributesConsentClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IoActivationServiceTest {

    private static final String FISCAL_CODE = "RSSMRA80A01H501U";
    private static final String UID = "uid-1";
    private static final String CX_ID = "cx-1";
    private static final Integer TOS_VERSION = 3;
    private static final Integer DATAPRIVACY_VERSION = 5;

    @Mock
    private IOLegalClient ioLegalClient;

    @Mock
    private PrivacyNoticeService privacyNoticeService;

    @Mock
    private UserAttributesConsentClient userAttributesConsentClient;

    @InjectMocks
    private IoActivationService service;

    private static ActivationPayload payload(ActivationStatus status) {
        return new ActivationPayload().fiscalCode(FISCAL_CODE).status(status);
    }

    private static it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Activation ioActivation(String status, Integer version) {
        return new it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Activation()
                .fiscalCode(FISCAL_CODE)
                .status(status)
                .version(version);
    }

    private void stubVersions() {
        when(privacyNoticeService.findPrivacyNoticeVersion("TOS", "PF")).thenReturn(TOS_VERSION);
        when(privacyNoticeService.findPrivacyNoticeVersion("DATAPRIVACY", "PF")).thenReturn(DATAPRIVACY_VERSION);
    }

    private Activation upsert(ActivationStatus status) {
        return service.upsertActivation(UID, CxTypeAuthFleet.PF, CX_ID, payload(status));
    }

    @Test
    void upsertActivation_active_acceptsTosThenDataPrivacyThenUpsertsOnIo() {
        stubVersions();
        when(ioLegalClient.upsertActivation(any())).thenReturn(ioActivation("ACTIVE", 7));

        Activation result = upsert(ActivationStatus.ACTIVE);

        InOrder order = inOrder(privacyNoticeService, userAttributesConsentClient, ioLegalClient);
        order.verify(privacyNoticeService).findPrivacyNoticeVersion("TOS", "PF");
        order.verify(userAttributesConsentClient).acceptConsent(UID, CX_ID,
                it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.CxTypeAuthFleet.PF,
                ConsentType.TOS, TOS_VERSION);
        order.verify(privacyNoticeService).findPrivacyNoticeVersion("DATAPRIVACY", "PF");
        order.verify(userAttributesConsentClient).acceptConsent(UID, CX_ID,
                it.pagopa.pn.ioconnector.generated.openapi.msclient.userattributes.v1.dto.CxTypeAuthFleet.PF,
                ConsentType.DATAPRIVACY, DATAPRIVACY_VERSION);
        order.verify(ioLegalClient).upsertActivation(any());
        assertThat(result.getFiscalCode()).isEqualTo(FISCAL_CODE);
        assertThat(result.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(result.getVersion()).isEqualTo(7);
    }

    @Test
    void upsertActivation_active_sendsFiscalCodeAndStatusToIo() {
        stubVersions();
        when(ioLegalClient.upsertActivation(any())).thenReturn(ioActivation("ACTIVE", 1));

        upsert(ActivationStatus.ACTIVE);

        ArgumentCaptor<it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ActivationPayload> captor =
                ArgumentCaptor.forClass(it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ActivationPayload.class);
        verify(ioLegalClient).upsertActivation(captor.capture());
        assertThat(captor.getValue().getFiscalCode()).isEqualTo(FISCAL_CODE);
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void upsertActivation_inactive_skipsConsentsAndUpsertsOnIo() {
        when(ioLegalClient.upsertActivation(any())).thenReturn(ioActivation("INACTIVE", 2));

        Activation result = upsert(ActivationStatus.INACTIVE);

        verifyNoInteractions(privacyNoticeService, userAttributesConsentClient);
        ArgumentCaptor<it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ActivationPayload> captor =
                ArgumentCaptor.forClass(it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.ActivationPayload.class);
        verify(ioLegalClient).upsertActivation(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("INACTIVE");
        assertThat(result.getStatus()).isEqualTo(ActivationStatus.INACTIVE);
        assertThat(result.getVersion()).isEqualTo(2);
    }

    @Test
    void upsertActivation_tosConsentFails_doesNotAcceptDataPrivacyNorCallIo() {
        PnHttpResponseException error = new PnHttpResponseException("user-attributes error", 500);
        when(privacyNoticeService.findPrivacyNoticeVersion("TOS", "PF")).thenReturn(TOS_VERSION);
        doThrow(error).when(userAttributesConsentClient).acceptConsent(any(), any(), any(), eq(ConsentType.TOS), any());

        assertThatThrownBy(() -> upsert(ActivationStatus.ACTIVE)).isSameAs(error);

        verify(privacyNoticeService, never()).findPrivacyNoticeVersion(eq("DATAPRIVACY"), any());
        verify(userAttributesConsentClient, never()).acceptConsent(any(), any(), any(), eq(ConsentType.DATAPRIVACY), any());
        verifyNoInteractions(ioLegalClient);
    }

    @Test
    void upsertActivation_dataPrivacyConsentFails_doesNotCallIo() {
        PnHttpResponseException error = new PnHttpResponseException("user-attributes error", 500);
        stubVersions();
        lenient().doThrow(error).when(userAttributesConsentClient).acceptConsent(any(), any(), any(), eq(ConsentType.DATAPRIVACY), any());

        assertThatThrownBy(() -> upsert(ActivationStatus.ACTIVE)).isSameAs(error);

        verify(userAttributesConsentClient).acceptConsent(any(), any(), any(), eq(ConsentType.TOS), any());
        verifyNoInteractions(ioLegalClient);
    }

    @Test
    void upsertActivation_versionResolutionFails_propagatesAndDoesNotCallIo() {
        RuntimeException error = new IllegalStateException("version lookup failed");
        when(privacyNoticeService.findPrivacyNoticeVersion("TOS", "PF")).thenThrow(error);

        assertThatThrownBy(() -> upsert(ActivationStatus.ACTIVE)).isSameAs(error);

        verifyNoInteractions(userAttributesConsentClient, ioLegalClient);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 429, 500, 503})
    void upsertActivation_ioUpsertFails_returnsActivationReadFromIo(int statusCode) {
        stubVersions();
        when(ioLegalClient.upsertActivation(any())).thenThrow(new PnHttpResponseException("io error", statusCode));
        when(ioLegalClient.getActivation(any())).thenReturn(ioActivation("INACTIVE", 4));

        Activation result = upsert(ActivationStatus.ACTIVE);

        ArgumentCaptor<FiscalCodePayload> captor = ArgumentCaptor.forClass(FiscalCodePayload.class);
        verify(ioLegalClient).getActivation(captor.capture());
        assertThat(captor.getValue().getFiscalCode()).isEqualTo(FISCAL_CODE);
        assertThat(result.getFiscalCode()).isEqualTo(FISCAL_CODE);
        assertThat(result.getStatus()).isEqualTo(ActivationStatus.INACTIVE);
        assertThat(result.getVersion()).isEqualTo(4);
    }

    @Test
    void upsertActivation_ioCommunicationFails_returnsActivationReadFromIo() {
        stubVersions();
        when(ioLegalClient.upsertActivation(any())).thenThrow(new ResourceAccessException("connection refused"));
        when(ioLegalClient.getActivation(any())).thenReturn(ioActivation("ACTIVE", 5));

        Activation result = upsert(ActivationStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(result.getVersion()).isEqualTo(5);
    }

    @Test
    void upsertActivation_ioUpsertFailsWithNonHttpError_propagatesWithoutFallback() {
        IllegalStateException error = new IllegalStateException("unexpected");
        stubVersions();
        when(ioLegalClient.upsertActivation(any())).thenThrow(error);

        assertThatThrownBy(() -> upsert(ActivationStatus.ACTIVE)).isSameAs(error);

        verify(ioLegalClient, never()).getActivation(any());
    }

    @Test
    void upsertActivation_inactiveAndIoUpsertFails_returnsActivationReadFromIo() {
        when(ioLegalClient.upsertActivation(any())).thenThrow(new PnHttpResponseException("io error", 500));
        when(ioLegalClient.getActivation(any())).thenReturn(ioActivation("ACTIVE", 9));

        Activation result = upsert(ActivationStatus.INACTIVE);

        verifyNoInteractions(privacyNoticeService, userAttributesConsentClient);
        assertThat(result.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(result.getVersion()).isEqualTo(9);
    }

    @Test
    void upsertActivation_ioUpsertAndFallbackReadFail_propagatesReadError() {
        PnHttpResponseException readError = new PnHttpResponseException("io read error", 503);
        stubVersions();
        when(ioLegalClient.upsertActivation(any())).thenThrow(new PnHttpResponseException("io error", 500));
        when(ioLegalClient.getActivation(any())).thenThrow(readError);

        assertThatThrownBy(() -> upsert(ActivationStatus.ACTIVE)).isSameAs(readError);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "PENDING"})
    void upsertActivation_mapsIoStatusOneToOne(String ioStatus) {
        stubVersions();
        when(ioLegalClient.upsertActivation(any())).thenReturn(ioActivation(ioStatus, 1));

        Activation result = upsert(ActivationStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(ActivationStatus.fromValue(ioStatus));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "PENDING"})
    void getActivation_delegatesToIoAndMapsStatus(String ioStatus) {
        when(ioLegalClient.getActivation(any())).thenReturn(ioActivation(ioStatus, 6));

        Activation result = service.getActivation(new it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.FiscalCodePayload().fiscalCode(FISCAL_CODE));

        ArgumentCaptor<FiscalCodePayload> captor = ArgumentCaptor.forClass(FiscalCodePayload.class);
        verify(ioLegalClient).getActivation(captor.capture());
        assertThat(captor.getValue().getFiscalCode()).isEqualTo(FISCAL_CODE);
        assertThat(result.getFiscalCode()).isEqualTo(FISCAL_CODE);
        assertThat(result.getStatus()).isEqualTo(ActivationStatus.fromValue(ioStatus));
        assertThat(result.getVersion()).isEqualTo(6);
        verifyNoInteractions(privacyNoticeService, userAttributesConsentClient);
    }

    @Test
    void getActivation_ioFails_propagatesError() {
        PnHttpResponseException error = new PnHttpResponseException("io error", 500);
        when(ioLegalClient.getActivation(any())).thenThrow(error);

        assertThatThrownBy(() -> service.getActivation(
                new it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.FiscalCodePayload().fiscalCode(FISCAL_CODE)))
                .isSameAs(error);
    }
}
