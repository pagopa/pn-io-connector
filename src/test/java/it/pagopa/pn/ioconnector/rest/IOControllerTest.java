package it.pagopa.pn.ioconnector.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import it.pagopa.pn.ioconnector.exceptions.PnIoGetMessageNotFoundException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetMessageResponseDetails;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileRequest;
import it.pagopa.pn.ioconnector.springbootcfg.PnErrorWebExceptionHandlerActivation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetProfileResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.service.io.GetMessageService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockitoBean
    private GetMessageService getMessageService;

    @Test
    void sendIOMessageAccepted() throws Exception {
        MessageResponse response = new MessageResponse()
                .requestId("REQ-TEST-001")
                .xPagopaIoConCxId("pn-delivery-push")
                .status(MessageResponse.StatusEnum.ACCEPTED);

        when(messageService.handleSendRequest(eq("pn-delivery-push"), any(MessageRequest.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(post("/io/message")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildMessageRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("REQ-TEST-001"))
                .andExpect(jsonPath("$.xPagopaIoConCxId").value("pn-delivery-push"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void sendIOMessage_duplicate_returns204() throws Exception {
        when(messageService.handleSendRequest(eq("pn-delivery-push"), any(MessageRequest.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/io/message")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildMessageRequest())))
                .andExpect(status().isNoContent());
    }

    @Test
    void sendIOMessage_conflict_returns409() throws Exception {
        when(messageService.handleSendRequest(any(), any())).thenThrow(
                new PnRuntimeException("conflict", "conflict", HttpStatus.CONFLICT.value(), new ArrayList<>())
        );

        mockMvc.perform(post("/io/message")
                .header("x-pagopa-iocon-cx-id", "pn-delivery-push")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildMessageRequest())))
                .andExpect(status().isConflict());
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

    @Test
    void getMessage_200() throws Exception {
        GetMessageResponse response = new GetMessageResponse()
                .details(new GetMessageResponseDetails()
                        .subject("Oggetto notifica")
                        .markdown("Testo notifica"));

        when(getMessageService.getMessageDetails(eq("test-request-id"), eq("FISCALCODE12345X")))
                .thenReturn(response);

        mockMvc.perform(get("/messages/{id}", "test-request-id")
                .header("x-pagopa-cx-taxid", "FISCALCODE12345X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.subject").value("Oggetto notifica"))
                .andExpect(jsonPath("$.details.markdown").value("Testo notifica"));
    }

    @Test
    void getMessage_404_fiscalCodeMismatch() throws Exception {
        when(getMessageService.getMessageDetails(eq("test-request-id"), any()))
                .thenThrow(new PnIoGetMessageNotFoundException("test-request-id"));

        mockMvc.perform(get("/messages/{id}", "test-request-id")
                .header("x-pagopa-cx-taxid", "DIFFERENT_CODE_X"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMessage_404() throws Exception {
        when(getMessageService.getMessageDetails(eq("test-request-id"), any()))
                .thenThrow(new PnIoGetMessageNotFoundException("test-request-id"));

        mockMvc.perform(get("/messages/{id}", "test-request-id")
                .header("x-pagopa-cx-taxid", "FISCALCODE12345X"))
                .andExpect(status().isNotFound());
    }

    private MessageRequest buildMessageRequest() {
        return new MessageRequest()
                .requestId("REQ-TEST-001")
                .iun("ABCD-EFGH-1234-5678-X")
                .recipientTaxId("ANON123456789")
                .senderServiceId("000000000")
                .subject("Notifica di test")
                .markdown("Hai ricevuto una notifica di test.");
    }

    private GetProfileRequest buildProfileRequest() {
        return new GetProfileRequest()
                .recipientTaxId("ANON123456789")
                .senderServiceId("000000000");
    }
}
