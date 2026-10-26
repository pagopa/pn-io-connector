package it.pagopa.pn.ioconnector.rest.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import it.pagopa.pn.ioconnector.exceptions.PnPrivacyNoticeNotFoundException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.Activation;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationPayload;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.ActivationStatus;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.CxTypeAuthFleet;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.FiscalCodePayload;
import it.pagopa.pn.ioconnector.service.legal.IoActivationService;
import it.pagopa.pn.ioconnector.springbootcfg.PnErrorWebExceptionHandlerActivation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IoActivationController.class)
@Import(PnErrorWebExceptionHandlerActivation.class)
class IoActivationControllerTest {

    private static final String FISCAL_CODE = "RSSMRA80A01H501U";
    private static final String PATH = "/io-connector/activations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IoActivationService ioActivationService;

    private MockHttpServletRequestBuilder putRequest() throws Exception {
        ActivationPayload payload = new ActivationPayload().fiscalCode(FISCAL_CODE).status(ActivationStatus.ACTIVE);
        return put(PATH)
                .header("x-pagopa-pn-uid", "uid-1")
                .header("x-pagopa-pn-cx-type", "PF")
                .header("x-pagopa-pn-cx-id", "cx-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    private MockHttpServletRequestBuilder postRequest() throws Exception {
        return post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FiscalCodePayload().fiscalCode(FISCAL_CODE)));
    }

    @Test
    void upsertIoActivation_success_returns200() throws Exception {
        when(ioActivationService.upsertActivation(eq("uid-1"), eq(CxTypeAuthFleet.PF), eq("cx-1"), any(ActivationPayload.class)))
                .thenReturn(new Activation().fiscalCode(FISCAL_CODE).status(ActivationStatus.ACTIVE).version(7));

        mockMvc.perform(putRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fiscal_code").value(FISCAL_CODE))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(7));
    }

    @Test
    void upsertIoActivation_consentsFail_returns500() throws Exception {
        when(ioActivationService.upsertActivation(any(), any(), any(), any()))
                .thenThrow(new PnHttpResponseException("consent error", 503));

        mockMvc.perform(putRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors[0].code").value(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_ACTIVATION_ERROR));
    }

    @Test
    void upsertIoActivation_missingPrivacyNoticeMapping_returns500() throws Exception {
        when(ioActivationService.upsertActivation(any(), any(), any(), any()))
                .thenThrow(new PnPrivacyNoticeNotFoundException("TOS", "PF"));

        mockMvc.perform(putRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors[0].code").value(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_ACTIVATION_ERROR));
    }

    @Test
    void upsertIoActivation_userAttributesClientError_returns500() throws Exception {
        when(ioActivationService.upsertActivation(any(), any(), any(), any()))
                .thenThrow(new PnHttpResponseException("user attributes error", 422));

        mockMvc.perform(putRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors[0].code").value(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_ACTIVATION_ERROR));
    }

    @Test
    void upsertIoActivation_fallbackReadFailsWith404_returns500() throws Exception {
        when(ioActivationService.upsertActivation(any(), any(), any(), any()))
                .thenThrow(new PnHttpResponseException("recipient not found", 404));

        mockMvc.perform(putRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors[0].code").value(PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_ACTIVATION_ERROR));
    }

    @Test
    void getIoActivation_success_returns200() throws Exception {
        when(ioActivationService.getActivation(any(FiscalCodePayload.class)))
                .thenReturn(new Activation().fiscalCode(FISCAL_CODE).status(ActivationStatus.INACTIVE).version(2));

        mockMvc.perform(postRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void getIoActivation_recipientNotFound_returns404() throws Exception {
        when(ioActivationService.getActivation(any(FiscalCodePayload.class)))
                .thenThrow(new PnHttpResponseException("recipient not found", 404));

        mockMvc.perform(postRequest())
                .andExpect(status().isNotFound());
    }
}
