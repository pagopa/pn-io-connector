 package it.pagopa.pn.ioconnector.rest.legal;

import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.SendMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.UserStatusResponse;
import it.pagopa.pn.ioconnector.service.legal.SendIoMessageService;
import it.pagopa.pn.ioconnector.springbootcfg.PnErrorWebExceptionHandlerActivation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SendIoMessageController.class)
@Import(PnErrorWebExceptionHandlerActivation.class)
class SendIoMessageControllerTest {

    private static final String SEND_MESSAGE_PATH = "/io-connector/sendmessage";
    private static final String USER_STATUS_PATH = "/io-connector/user-status";
    private static final String TAX_ID = "RSSMRA80A01H501U";

    // Body inviato da pn-user-attributes: contiene "amount", non più nel contratto
    private static final String USER_ATTRIBUTES_BODY = """
            {
              "requestId": "REQ-001",
              "iun": "IUN-001",
              "recipientTaxID": "RSSMRA80A01H501U",
              "recipientInternalID": "PF-internal-001",
              "recipientIndex": 0,
              "senderDenomination": "Comune di Milano",
              "subject": "Comune di Milano - notifica",
              "amount": 1500,
              "due_date": "2026-03-31T00:00:00+02:00",
              "requestAcceptedDate": "2026-03-01T10:15:30.123Z",
              "carbonCopyToDeliveryPush": true
            }
            """;

    // Body inviato da pn-delivery-push-workflow: contiene campi non più nel contratto
    private static final String DELIVERY_PUSH_BODY = """
            {
              "iun": "IUN-002",
              "recipientTaxID": "RSSMRA80A01H501U",
              "recipientInternalID": "PF-internal-002",
              "recipientIndex": 1,
              "senderDenomination": "Comune di Roma",
              "subject": "Comune di Roma - notifica",
              "noticeNumber": "302000100000019421",
              "creditorTaxId": "77777777777",
              "schedulingAnalogDate": "2026-03-10T10:00:00Z",
              "requestAcceptedDate": "2026-03-01T10:15:30Z",
              "deliveryMode": "ANALOG"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SendIoMessageService sendIoMessageService;

    @Test
    void sendLegalIOMessage_userAttributesBody_returns200WithDueDateInUtc() throws Exception {
        when(sendIoMessageService.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(new SendMessageResponse().result(SendMessageResponse.ResultEnum.SENT_COURTESY).id("IO-MSG-001"));

        mockMvc.perform(post(SEND_MESSAGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER_ATTRIBUTES_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SENT_COURTESY"))
                .andExpect(jsonPath("$.id").value("IO-MSG-001"));

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sendIoMessageService).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();
        assertThat(request.getIun()).isEqualTo("IUN-001");
        assertThat(request.getDueDate()).isEqualTo(Instant.parse("2026-03-30T22:00:00Z"));
        assertThat(request.getRequestAcceptedDate()).isEqualTo(Instant.parse("2026-03-01T10:15:30.123Z"));
        assertThat(request.getCarbonCopyToDeliveryPush()).isTrue();
    }

    @Test
    void sendLegalIOMessage_deliveryPushBody_returns200() throws Exception {
        when(sendIoMessageService.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(new SendMessageResponse().result(SendMessageResponse.ResultEnum.SENT_OPTIN).id("IO-MSG-002"));

        mockMvc.perform(post(SEND_MESSAGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DELIVERY_PUSH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SENT_OPTIN"));

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sendIoMessageService).sendMessage(captor.capture());
        SendMessageRequest request = captor.getValue();
        assertThat(request.getIun()).isEqualTo("IUN-002");
        assertThat(request.getRecipientIndex()).isEqualTo(1);
        assertThat(request.getDeliveryMode()).isEqualTo(SendMessageRequest.DeliveryModeEnum.ANALOG);
        assertThat(request.getDueDate()).isNull();
        assertThat(request.getCarbonCopyToDeliveryPush()).isFalse();
    }

    @Test
    void sendLegalIOMessage_missingRequiredField_returns400() throws Exception {
        String body = """
                {
                  "iun": "IUN-003",
                  "recipientTaxID": "RSSMRA80A01H501U",
                  "recipientInternalID": "PF-internal-003",
                  "senderDenomination": "Comune di Torino"
                }
                """;

        mockMvc.perform(post(SEND_MESSAGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(sendIoMessageService);
    }

    @Test
    void getIoUserStatus_returns200() throws Exception {
        when(sendIoMessageService.getUserStatus(any(UserStatusRequest.class)))
                .thenReturn(new UserStatusResponse().taxId(TAX_ID).status(UserStatusResponse.StatusEnum.PN_ACTIVE));

        mockMvc.perform(post(USER_STATUS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxId\": \"" + TAX_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxId").value(TAX_ID))
                .andExpect(jsonPath("$.status").value("PN_ACTIVE"));
    }
}
