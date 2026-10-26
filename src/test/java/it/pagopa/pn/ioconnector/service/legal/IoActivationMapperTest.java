package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IoActivationMapperTest {

    private static final String FISCAL_CODE = "RSSMRA80A01H501U";

    @Test
    void toIoFiscalCodePayload_mapsFiscalCode() {
        FiscalCodePayload result = IoActivationMapper.toIoFiscalCodePayload(FISCAL_CODE);

        assertThat(result.getFiscalCode()).isEqualTo(FISCAL_CODE);
    }

    @ParameterizedTest
    @EnumSource(ActivationStatus.class)
    void toIoActivationPayload_mapsFiscalCodeAndStatus(ActivationStatus status) {
        var result = IoActivationMapper.toIoActivationPayload(
                new ActivationPayload().fiscalCode(FISCAL_CODE).status(status));

        assertThat(result.getFiscalCode()).isEqualTo(FISCAL_CODE);
        assertThat(result.getStatus()).isEqualTo(status.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "PENDING"})
    void toActivation_mapsFiscalCodeStatusAndVersion(String ioStatus) {
        var ioActivation = new it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.Activation()
                .fiscalCode(FISCAL_CODE)
                .status(ioStatus)
                .version(9);

        Activation result = IoActivationMapper.toActivation(ioActivation);

        assertThat(result.getFiscalCode()).isEqualTo(FISCAL_CODE);
        assertThat(result.getStatus()).isEqualTo(ActivationStatus.fromValue(ioStatus));
        assertThat(result.getVersion()).isEqualTo(9);
    }

    @ParameterizedTest
    @EnumSource(value = it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet.class,
            names = {"PA", "PF", "PG", "RADD"})
    void toUserAttributesCxType_mapsByValue(it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet cxType) {
        var result = IoActivationMapper.toUserAttributesCxType(cxType);

        assertThat(result.getValue()).isEqualTo(cxType.getValue());
    }

    @Test
    void toUserAttributesCxType_valueUnknownToUserAttributes_throwsIllegalArgument() {
        assertThatThrownBy(() -> IoActivationMapper.toUserAttributesCxType(
                it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet.BO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
