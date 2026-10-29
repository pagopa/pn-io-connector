package it.pagopa.pn.ioconnector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PnIoConnectorConfigPrivacyNoticeTest {

    private static final String PARAMETER_NAME = "MapPrivacyNotice";

    @Mock
    private SsmClient ssmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PnIoConnectorConfig config;

    @BeforeEach
    void setUp() {
        config = new PnIoConnectorConfig();
    }

    private void stubParameterValue(String value) {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(GetParameterResponse.builder()
                        .parameter(Parameter.builder().name(PARAMETER_NAME).value(value).build())
                        .build());
    }

    @Test
    void privacyNoticeMapping_parsesParameterValue() {
        stubParameterValue("""
                [{"consentsType":"TOS","portalType":"PF","privacyNoticeId":"id-1","defaultVersion":3},
                 {"consentsType":"DATAPRIVACY","portalType":"PF","privacyNoticeId":"id-2","defaultVersion":4}]""");

        PrivacyNoticeMapping mapping = config.privacyNoticeMapping(ssmClient, objectMapper);

        assertThat(mapping.notices()).containsExactly(
                new PrivacyNoticeDTO("TOS", "PF", "id-1", 3),
                new PrivacyNoticeDTO("DATAPRIVACY", "PF", "id-2", 4));
    }

    @Test
    void privacyNoticeMapping_readsTheRootPathParameter() {
        stubParameterValue("[]");

        config.privacyNoticeMapping(ssmClient, objectMapper);

        ArgumentCaptor<GetParameterRequest> captor = ArgumentCaptor.forClass(GetParameterRequest.class);
        verify(ssmClient).getParameter(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo(PARAMETER_NAME);
    }

    @Test
    void privacyNoticeMapping_throwsWhenParameterIsMissing() {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(ParameterNotFoundException.builder().message("not found").build());

        assertThatThrownBy(() -> config.privacyNoticeMapping(ssmClient, objectMapper))
                .isInstanceOfSatisfying(PnInternalException.class, e -> {
                    assertThat(e.getProblem().getDetail()).contains(PARAMETER_NAME);
                    assertThat(e.getProblem().getErrors().get(0).getCode())
                            .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PARAMETERSTORE_ERROR);
                });
    }

    @Test
    void privacyNoticeMapping_throwsWhenJsonIsMalformed() {
        stubParameterValue("[{\"consentsType\": ");

        assertThatThrownBy(() -> config.privacyNoticeMapping(ssmClient, objectMapper))
                .isInstanceOfSatisfying(PnInternalException.class, e ->
                        assertThat(e.getProblem().getErrors().get(0).getCode())
                                .isEqualTo(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PARAMETERSTORE_ERROR));
    }
}
