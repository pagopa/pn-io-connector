package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PnIoConnectorConfigTest {

    private static final String PARAMETER_NAME = "IoConnectorServiceConfiguration";

    @Mock private SsmClient ssmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PnIoConnectorConfig config;

    @BeforeEach
    void setUp() {
        config = new PnIoConnectorConfig();
        config.setServiceConfigurationsParameterName(PARAMETER_NAME);
    }

    @Test
    void ioServiceConfigurationResolver_parsesParameterValue() {
        stubParameterValue("{\"SVC-001\":{\"configurationId\":\"CONF-001\",\"organizationFiscalCode\":\"12345678910\"},"
                + "\"SVC-002\":{\"configurationId\":\"CONF-002\",\"organizationFiscalCode\":\"00987654321\"}}");

        IoServiceConfigurationResolver resolver = config.ioServiceConfigurationResolver(ssmClient, objectMapper);

        assertThat(resolver.getConfiguration("SVC-001"))
                .isEqualTo(new IoServiceConfiguration("CONF-001", "12345678910"));
        assertThat(resolver.getConfigurationId("SVC-002")).isEqualTo("CONF-002");
    }

    @Test
    void ioServiceConfigurationResolver_throwsWhenParameterIsMissing() {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().message("not found").build());

        assertThatThrownBy(() -> config.ioServiceConfigurationResolver(ssmClient, objectMapper))
                .isInstanceOfSatisfying(PnInternalException.class, e -> {
                    assertThat(e.getProblem().getDetail()).contains(PARAMETER_NAME);
                    assertThat(e.getProblem().getErrors().get(0).getCode())
                            .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PARAMETERSTORE_ERROR);
                });
    }

    @Test
    void ioServiceConfigurationResolver_throwsWhenJsonIsMalformed() {
        stubParameterValue("{\"SVC-001\": ");

        assertThatThrownBy(() -> config.ioServiceConfigurationResolver(ssmClient, objectMapper))
                .isInstanceOfSatisfying(PnInternalException.class, e ->
                        assertThat(e.getProblem().getErrors().get(0).getCode())
                                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PARAMETERSTORE_ERROR));
    }

    private void stubParameterValue(String value) {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(GetParameterResponse.builder()
                        .parameter(Parameter.builder().name(PARAMETER_NAME).value(value).build())
                        .build());
    }
}
