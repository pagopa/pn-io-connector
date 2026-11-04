package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.config.PrivacyNoticeDTO;
import it.pagopa.pn.ioconnector.config.PrivacyNoticeMapping;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.exceptions.PnPrivacyNoticeNotFoundException;
import it.pagopa.pn.ioconnector.middleware.msclient.onetrust.OneTrustClient;
import it.pagopa.pn.ioconnector.middleware.msclient.onetrust.PrivacyNoticeOneTrustResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyNoticeServiceTest {

    private static final String TOS_PF_ID = "id-tos-pf";
    private static final String TOS_PG_ID = "id-tos-pg";
    private static final String DATAPRIVACY_PF_ID = "id-dataprivacy-pf";

    @Mock
    private OneTrustClient oneTrustClient;

    private PrivacyNoticeService service;

    @BeforeEach
    void setUp() {
        PrivacyNoticeMapping mapping = new PrivacyNoticeMapping(List.of(
                new PrivacyNoticeDTO("TOS", "PF", TOS_PF_ID, 3),
                new PrivacyNoticeDTO("DATAPRIVACY", "PF", DATAPRIVACY_PF_ID, 4),
                new PrivacyNoticeDTO("TOS", "PG", TOS_PG_ID, 5)));
        service = new PrivacyNoticeService(mapping, oneTrustClient);
    }

    private static PrivacyNoticeOneTrustResult oneTrustResult(int version) {
        return new PrivacyNoticeOneTrustResult(version);
    }

    @Test
    void findPrivacyNoticeVersion_oneTrustAvailable_returnsPublishedVersion() {
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID)).thenReturn(oneTrustResult(7));

        Integer version = service.findPrivacyNoticeVersion("TOS", "PF");

        assertThat(version).isEqualTo(7);
        verify(oneTrustClient).getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID);
    }

    @Test
    void findPrivacyNoticeVersion_selectsEntryByConsentsTypeAndPortalType() {
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PG_ID)).thenReturn(oneTrustResult(11));
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(DATAPRIVACY_PF_ID)).thenReturn(oneTrustResult(12));

        assertThat(service.findPrivacyNoticeVersion("TOS", "PG")).isEqualTo(11);
        assertThat(service.findPrivacyNoticeVersion("DATAPRIVACY", "PF")).isEqualTo(12);
    }

    @Test
    void findPrivacyNoticeVersion_oneTrustFailsAfterSuccess_returnsCachedVersion() {
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID))
                .thenReturn(oneTrustResult(7))
                .thenThrow(new PnHttpResponseException("onetrust down", 500));

        assertThat(service.findPrivacyNoticeVersion("TOS", "PF")).isEqualTo(7);
        assertThat(service.findPrivacyNoticeVersion("TOS", "PF")).isEqualTo(7);

        verify(oneTrustClient, times(2)).getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID);
    }

    @Test
    void findPrivacyNoticeVersion_cacheIsUpdatedOnEverySuccess() {
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID))
                .thenReturn(oneTrustResult(7))
                .thenReturn(oneTrustResult(8))
                .thenThrow(new PnHttpResponseException("onetrust down", 500));

        service.findPrivacyNoticeVersion("TOS", "PF");
        service.findPrivacyNoticeVersion("TOS", "PF");

        assertThat(service.findPrivacyNoticeVersion("TOS", "PF")).isEqualTo(8);
    }

    @Test
    void findPrivacyNoticeVersion_cacheIsKeyedByPrivacyNoticeId() {
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID)).thenReturn(oneTrustResult(7));
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(DATAPRIVACY_PF_ID))
                .thenThrow(new PnHttpResponseException("onetrust down", 500));

        service.findPrivacyNoticeVersion("TOS", "PF");

        assertThat(service.findPrivacyNoticeVersion("DATAPRIVACY", "PF")).isEqualTo(4);
    }

    @Test
    void findPrivacyNoticeVersion_oneTrustFailsWithoutCache_returnsDefaultVersion() {
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID))
                .thenThrow(new PnHttpResponseException("onetrust down", 503));

        assertThat(service.findPrivacyNoticeVersion("TOS", "PF")).isEqualTo(3);
    }

    @Test
    void findPrivacyNoticeVersion_oneTrustCommunicationFails_returnsDefaultVersion() {
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThat(service.findPrivacyNoticeVersion("TOS", "PF")).isEqualTo(3);
    }

    @Test
    void findPrivacyNoticeVersion_oneTrustTokenOrMappingError_returnsDefaultVersion() {
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID))
                .thenThrow(new PnInternalException("mapping error",
                        PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_MAPPING_ERROR));

        assertThat(service.findPrivacyNoticeVersion("TOS", "PF")).isEqualTo(3);
    }

    @Test
    void findPrivacyNoticeVersion_oneTrustFailsWithProgrammingError_propagatesWithoutFallback() {
        IllegalStateException error = new IllegalStateException("unexpected");
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID)).thenThrow(error);

        assertThatThrownBy(() -> service.findPrivacyNoticeVersion("TOS", "PF")).isSameAs(error);
    }

    @Test
    void findPrivacyNoticeVersion_oneTrustFailsWithoutCacheAndNullDefaultVersion_throwsMappingError() {
        service = new PrivacyNoticeService(new PrivacyNoticeMapping(List.of(
                new PrivacyNoticeDTO("TOS", "PF", TOS_PF_ID, null))), oneTrustClient);
        when(oneTrustClient.getPrivacyNoticeVersionByPrivacyNoticeId(TOS_PF_ID))
                .thenThrow(new PnHttpResponseException("onetrust down", 503));

        assertThatThrownBy(() -> service.findPrivacyNoticeVersion("TOS", "PF"))
                .isInstanceOfSatisfying(PnInternalException.class, e ->
                        assertThat(e.getProblem().getErrors().get(0).getCode())
                                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_MAPPING_ERROR));
    }

    @Test
    void findPrivacyNoticeVersion_entryMissing_throwsPrivacyNoticeNotFound() {
        assertThatThrownBy(() -> service.findPrivacyNoticeVersion("TOS", "PA"))
                .isInstanceOfSatisfying(PnPrivacyNoticeNotFoundException.class, e -> {
                    assertThat(e.getProblem().getErrors().get(0).getCode())
                            .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_NOT_FOUND);
                    assertThat(e.getProblem().getStatus()).isEqualTo(500);
                });

        verifyNoInteractions(oneTrustClient);
    }

    @Test
    void findPrivacyNoticeVersion_emptyMapping_throwsPrivacyNoticeNotFound() {
        service = new PrivacyNoticeService(new PrivacyNoticeMapping(List.of()), oneTrustClient);

        assertThatThrownBy(() -> service.findPrivacyNoticeVersion("TOS", "PF"))
                .isInstanceOf(PnPrivacyNoticeNotFoundException.class);

        verifyNoInteractions(oneTrustClient);
    }
}
