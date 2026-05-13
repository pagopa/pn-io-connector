package it.pagopa.pn.ioconnector.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileRequest;
import it.pagopa.pn.ioconnector.springbootcfg.PnErrorWebExceptionHandlerActivation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.service.io.MessageService;
import it.pagopa.pn.ioconnector.service.io.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = IOController.class)
@Import(PnErrorWebExceptionHandlerActivation.class)
class IOControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private ProfileService profileService;

    @Test
    void sendIOMessageAccepted() throws Exception {
        MessageResponse response = new MessageResponse()
                .requestId("REQ-TEST-001")
                .cxId("pn-delivery-push")
                .status(MessageResponse.StatusEnum.ACCEPTED);

        when(messageService.handleSendRequest(eq("pn-delivery-push"), any(MessageRequest.class))).thenReturn(response);

        mockMvc.perform(post("/io/message")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildMessageRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value("REQ-TEST-001"))
                .andExpect(jsonPath("$.cxId").value("pn-delivery-push"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void sendIOMessageNotAccepted() throws Exception {
        MessageResponse response = new MessageResponse()
                .requestId("REQ-TEST-001")
                .cxId("pn-delivery-push")
                .status(MessageResponse.StatusEnum.NOT_ACCEPTED);

        when(messageService.handleSendRequest(eq("pn-delivery-push"), any(MessageRequest.class))).thenReturn(response);

        mockMvc.perform(post("/io/message")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildMessageRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("REQ-TEST-001"))
                .andExpect(jsonPath("$.cxId").value("pn-delivery-push"))
                .andExpect(jsonPath("$.status").value("NOT_ACCEPTED"));
    }

    @Test
    void sendIOMessageMissingCxIdHeader() throws Exception {
        mockMvc.perform(post("/io/message")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildMessageRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendIOMessageMissingRequiredFields() throws Exception {
        MessageRequest invalidRequest = new MessageRequest()
                .requestId("REQ-TEST-002");

        mockMvc.perform(post("/io/message")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getIOProfileSenderAllowed() throws Exception {
        GetProfileResponse response = new GetProfileResponse()
                .status(GetProfileResponse.StatusEnum.SENDER_ALLOWED)
                .preferredLanguages(List.of("it_IT", "en_US"));

        when(profileService.getProfile(any(GetProfileRequest.class))).thenReturn(response);

        mockMvc.perform(post("/io/profile")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProfileRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENDER_ALLOWED"))
                .andExpect(jsonPath("$.preferredLanguages[0]").value("it_IT"))
                .andExpect(jsonPath("$.preferredLanguages[1]").value("en_US"));
    }

    @Test
    void getIOProfileSenderNotAllowed() throws Exception {
        GetProfileResponse response = new GetProfileResponse()
                .status(GetProfileResponse.StatusEnum.SENDER_NOT_ALLOWED);

        when(profileService.getProfile(any(GetProfileRequest.class))).thenReturn(response);

        mockMvc.perform(post("/io/profile")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProfileRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENDER_NOT_ALLOWED"));
    }

    @Test
    void getIOProfileMissingCxIdHeader() throws Exception {
        mockMvc.perform(post("/io/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProfileRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getIOProfileMissingRequiredFields() throws Exception {
        GetProfileRequest invalidRequest = new GetProfileRequest()
                .recipientTaxId("ANON123456789");

        mockMvc.perform(post("/io/profile")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendIOMessage_internalServerError() throws Exception {
        when(messageService.handleSendRequest(any(), any())).thenThrow(
                new PnRuntimeException("error", "error", HttpStatus.INTERNAL_SERVER_ERROR.value(), new ArrayList<>())
        );

        mockMvc.perform(post("/io/message")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildMessageRequest())))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getIOProfile_internalServerError() throws Exception {
        when(profileService.getProfile(any())).thenThrow(
                new PnRuntimeException("error", "error", HttpStatus.INTERNAL_SERVER_ERROR.value(), new ArrayList<>())
        );

        mockMvc.perform(post("/io/profile")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProfileRequest())))
                .andExpect(status().isInternalServerError());
    }

    private MessageRequest buildMessageRequest() {
        return new MessageRequest()
                .requestId("REQ-TEST-001")
                .iun("ABCD-EFGH-1234-5678-X")
                .recipientTaxId("ANON123456789")
                .senderTaxId("12345678901")
                .senderServiceId("000000000")
                .subject("Notifica di test")
                .markdown("Hai ricevuto una notifica di test.");
    }

    private GetProfileRequest buildProfileRequest() {
        return new GetProfileRequest()
                .recipientTaxId("ANON123456789")
                .senderTaxId("12345678901")
                .senderServiceId("000000000");
    }
}
