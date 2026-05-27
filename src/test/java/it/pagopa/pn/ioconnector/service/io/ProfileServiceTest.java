package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileResponse;
import it.pagopa.pn.ioconnector.service.DataVaultService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private IOService ioService;
    @Mock private DataVaultService dataVaultService;

    @InjectMocks private ProfileService profileService;

    @Test
    void getProfile_senderAllowed() {
        when(ioService.getServiceUseKey("SVC-001")).thenReturn("key");
        when(dataVaultService.deanonymize("ANON-TAX")).thenReturn("CF");
        when(ioService.checkUserProfile("CF", "key")).thenReturn(new LimitedProfile().senderAllowed(true));

        GetProfileResponse result = profileService.getProfile(buildRequest());

        assertThat(result.getStatus()).isEqualTo(GetProfileResponse.StatusEnum.SENDER_ALLOWED);
    }

    @Test
    void getProfile_senderNotAllowed() {
        when(ioService.getServiceUseKey("SVC-001")).thenReturn("key");
        when(dataVaultService.deanonymize("ANON-TAX")).thenReturn("CF");
        when(ioService.checkUserProfile("CF", "key")).thenReturn(new LimitedProfile().senderAllowed(false));

        GetProfileResponse result = profileService.getProfile(buildRequest());

        assertThat(result.getStatus()).isEqualTo(GetProfileResponse.StatusEnum.SENDER_NOT_ALLOWED);
    }

    @Test
    void getProfile_throwsError() {
        when(ioService.getServiceUseKey("SVC-001")).thenThrow(
                new PnRuntimeException("IO error", "IO error", HttpStatus.INTERNAL_SERVER_ERROR.value(), new ArrayList<>())
        );

        assertThatThrownBy(() -> profileService.getProfile(buildRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("IO error");
    }

    @Test
    void getProfile_senderAllowed_withPreferredLanguages() {
        when(ioService.getServiceUseKey("SVC-001")).thenReturn("key");
        when(dataVaultService.deanonymize("ANON-TAX")).thenReturn("CF");
        when(ioService.checkUserProfile("CF", "key"))
                .thenReturn(new LimitedProfile().senderAllowed(true).preferredLanguages(List.of("it_IT", "en_US")));

        GetProfileResponse result = profileService.getProfile(buildRequest());

        assertThat(result.getPreferredLanguages()).containsExactly("it_IT", "en_US");
    }

    private GetProfileRequest buildRequest() {
        return new GetProfileRequest()
                .recipientTaxId("ANON-TAX")
                .senderServiceId("SVC-001");
    }
}
