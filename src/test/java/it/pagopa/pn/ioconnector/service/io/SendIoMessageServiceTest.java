package it.pagopa.pn.ioconnector.service.legal;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.generated.openapi.msclient.io.v1.dto.LimitedProfile;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import it.pagopa.pn.ioconnector.middleware.msclient.IOLegalClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendIoMessageServiceTest {

    private static final String TAX_ID = "RSSMRA80A01H501U";

    @Mock
    private IOLegalClient ioLegalClient;

    @InjectMocks
    private SendIoMessageService sendIoMessageService;

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
}
