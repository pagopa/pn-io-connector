package it.pagopa.pn.ioconnector.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.ExceptionHelper;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageRequest;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.MessageResponse;
import it.pagopa.pn.ioconnector.service.io.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MessageController.class)
@Import(ExceptionHelper.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MessageService messageService;

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
            .content(objectMapper.writeValueAsString(buildRequest())))
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
            .content(objectMapper.writeValueAsString(buildRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value("REQ-TEST-001"))
            .andExpect(jsonPath("$.cxId").value("pn-delivery-push"))
            .andExpect(jsonPath("$.status").value("NOT_ACCEPTED"));
    }

    @Test
    void sendIOMessageMissingCxIdHeader() throws Exception {
        mockMvc.perform(post("/io/message")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(buildRequest())))
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

    private MessageRequest buildRequest() {
        return new MessageRequest()
            .requestId("REQ-TEST-001")
            .iun("ABCD-EFGH-1234-5678-X")
            .recipientTaxId("ANON123456789")
            .senderTaxId("12345678901")
            .senderServiceId("000000000")
            .subject("Notifica di test")
            .markdown("Hai ricevuto una notifica di test.");
    }
}
