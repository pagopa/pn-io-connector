package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileResponse;
import it.pagopa.pn.ioconnector.service.DataVaultService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private IOService ioService;
    @Mock private DataVaultService dataVaultService;

    @InjectMocks private ProfileService profileService;

    @Test
    void getProfile_senderAllowed() {
        when(ioService.getServiceUseKey("SENDER-TAX", "SVC-001")).thenReturn("key");
        when(dataVaultService.deanonymize("ANON-TAX")).thenReturn("CF");
        when(ioService.checkUserProfile("CF", "key")).thenReturn(true);

        GetProfileResponse result = profileService.getProfile(buildRequest());

        assertThat(result.getStatus()).isEqualTo(GetProfileResponse.StatusEnum.SENDER_ALLOWED);
    }

    @Test
    void getProfile_senderNotAllowed() {
        when(ioService.getServiceUseKey("SENDER-TAX", "SVC-001")).thenReturn("key");
        when(dataVaultService.deanonymize("ANON-TAX")).thenReturn("CF");
        when(ioService.checkUserProfile("CF", "key")).thenReturn(false);

        GetProfileResponse result = profileService.getProfile(buildRequest());

        assertThat(result.getStatus()).isEqualTo(GetProfileResponse.StatusEnum.SENDER_NOT_ALLOWED);
    }

    private GetProfileRequest buildRequest() {
        return new GetProfileRequest()
            .recipientTaxId("ANON-TAX")
            .senderTaxId("SENDER-TAX")
            .senderServiceId("SVC-001");
    }
}
