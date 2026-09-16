package it.pagopa.pn.ioconnector.config;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IoServiceConfigurationResolverTest {

    @Test
    void getConfiguration_returnsBothFields() {
        IoServiceConfigurationResolver resolver = new IoServiceConfigurationResolver(
                Map.of("SVC-001", new IoServiceConfiguration("CONF-001", "12345678910")));

        IoServiceConfiguration configuration = resolver.getConfiguration("SVC-001");

        assertThat(configuration.configurationId()).isEqualTo("CONF-001");
        assertThat(configuration.organizationFiscalCode()).isEqualTo("12345678910");
        assertThat(resolver.getConfigurationId("SVC-001")).isEqualTo("CONF-001");
    }

    @Test
    void getOrganizationFiscalCode_returnsValue() {
        IoServiceConfigurationResolver resolver = new IoServiceConfigurationResolver(
                Map.of("SVC-001", new IoServiceConfiguration("CONF-001", "12345678910")));

        assertThat(resolver.getOrganizationFiscalCode("SVC-001")).isEqualTo("12345678910");
    }

    @Test
    void getOrganizationFiscalCode_throwsWhenOrganizationFiscalCodeIsNull() {
        IoServiceConfigurationResolver resolver = new IoServiceConfigurationResolver(
                Map.of("SVC-001", new IoServiceConfiguration("CONF-001", null)));

        assertThatThrownBy(() -> resolver.getOrganizationFiscalCode("SVC-001"))
                .isInstanceOfSatisfying(PnInternalException.class, e -> {
                    assertThat(e.getProblem().getDetail()).contains("SVC-001");
                    assertThat(e.getProblem().getErrors().get(0).getCode())
                            .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_MISSING_ORGANIZATION_FISCAL_CODE);
                });
    }

    @Test
    void getOrganizationFiscalCode_throwsWhenOrganizationFiscalCodeIsBlank() {
        IoServiceConfigurationResolver resolver = new IoServiceConfigurationResolver(
                Map.of("SVC-001", new IoServiceConfiguration("CONF-001", "  ")));

        assertThatThrownBy(() -> resolver.getOrganizationFiscalCode("SVC-001"))
                .isInstanceOfSatisfying(PnInternalException.class, e ->
                        assertThat(e.getProblem().getErrors().get(0).getCode())
                                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_MISSING_ORGANIZATION_FISCAL_CODE));
    }

    @Test
    void getOrganizationFiscalCode_throwsWhenServiceIdIsNotMapped() {
        IoServiceConfigurationResolver resolver = new IoServiceConfigurationResolver(Map.of());

        assertThatThrownBy(() -> resolver.getOrganizationFiscalCode("SVC-999"))
                .isInstanceOfSatisfying(PnInternalException.class, e ->
                        assertThat(e.getProblem().getErrors().get(0).getCode())
                                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_SERVICE_NOT_CONFIGURED));
    }

    @Test
    void getConfiguration_throwsWhenServiceIdIsNotMapped() {
        IoServiceConfigurationResolver resolver = new IoServiceConfigurationResolver(
                Map.of("SVC-001", new IoServiceConfiguration("CONF-001", "12345678910")));

        assertThatThrownBy(() -> resolver.getConfiguration("SVC-999"))
                .isInstanceOfSatisfying(PnInternalException.class, e -> {
                    assertThat(e.getProblem().getDetail()).contains("SVC-999");
                    assertThat(e.getProblem().getErrors().get(0).getCode())
                            .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_SERVICE_NOT_CONFIGURED);
                });
    }

    @Test
    void getConfigurationId_throwsWhenConfigurationIdIsBlank() {
        IoServiceConfigurationResolver resolver = new IoServiceConfigurationResolver(
                Map.of("SVC-001", new IoServiceConfiguration("  ", "12345678910")));

        assertThatThrownBy(() -> resolver.getConfigurationId("SVC-001"))
                .isInstanceOfSatisfying(PnInternalException.class, e ->
                        assertThat(e.getProblem().getErrors().get(0).getCode())
                                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_MISSING_IO_CONFIGURATION_ID));
    }

    @Test
    void getConfigurationId_throwsWhenEntryIsNull() {
        Map<String, IoServiceConfiguration> configurations = new HashMap<>();
        configurations.put("SVC-001", null);
        IoServiceConfigurationResolver resolver = new IoServiceConfigurationResolver(configurations);

        assertThatThrownBy(() -> resolver.getConfigurationId("SVC-001"))
                .isInstanceOf(PnInternalException.class);
    }
}
