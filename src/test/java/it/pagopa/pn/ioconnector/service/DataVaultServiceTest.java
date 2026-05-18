package it.pagopa.pn.ioconnector.service;

import it.pagopa.pn.ioconnector.exceptions.PnDataVaultException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.datavault.v1.dto.BaseRecipientDto;
import it.pagopa.pn.ioconnector.middleware.msclient.DataVaultClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.NoSuchElementException;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_DATAVAULT_DEANONYMIZE_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataVaultServiceTest {

    private static final String INTERNAL_ID = "INTERNAL-001";
    private static final String TAX_ID = "ABCDEF12G34H567I";

    @Mock
    private DataVaultClient dataVaultClient;

    @InjectMocks
    private DataVaultService dataVaultService;

    @Test
    void deanonymize_returnsTaxId() {
        when(dataVaultClient.deanonymize(INTERNAL_ID)).thenReturn(List.of(buildRecipient(INTERNAL_ID, TAX_ID)));

        String result = dataVaultService.deanonymize(INTERNAL_ID);

        assertThat(result).isEqualTo(TAX_ID);
    }

    @Test
    void deanonymize_multipleRecipients_returnsMatchingTaxId() {
        BaseRecipientDto other = buildRecipient("INTERNAL-002", "ZZZZZZZ");
        BaseRecipientDto target = buildRecipient(INTERNAL_ID, TAX_ID);
        when(dataVaultClient.deanonymize(INTERNAL_ID)).thenReturn(List.of(other, target));

        String result = dataVaultService.deanonymize(INTERNAL_ID);

        assertThat(result).isEqualTo(TAX_ID);
    }

    @Test
    void deanonymize_emptyList_throwsNoSuchElement() {
        when(dataVaultClient.deanonymize(INTERNAL_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> dataVaultService.deanonymize(INTERNAL_ID))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deanonymize_noMatchingInternalId_throwsNoSuchElement() {
        when(dataVaultClient.deanonymize(INTERNAL_ID)).thenReturn(List.of(buildRecipient("INTERNAL-OTHER", TAX_ID)));

        assertThatThrownBy(() -> dataVaultService.deanonymize(INTERNAL_ID))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deanonymize_clientThrows_propagatesException() {
        when(dataVaultClient.deanonymize(INTERNAL_ID)).thenThrow(
                new PnDataVaultException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "service error")
        );

        assertThatThrownBy(() -> dataVaultService.deanonymize(INTERNAL_ID))
                .isInstanceOf(PnDataVaultException.class);
    }

    private BaseRecipientDto buildRecipient(String internalId, String taxId) {
        BaseRecipientDto dto = new BaseRecipientDto();
        dto.setInternalId(internalId);
        dto.setTaxId(taxId);
        return dto;
    }
}
