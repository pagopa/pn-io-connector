package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.exceptions.PnIoGetMessageNotFoundException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetMessageResponse;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.ioconnector.utils.LogEventCaptor;
import it.pagopa.pn.ioconnector.utils.MdcTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMessageServiceTest {

    @Mock
    private IOConnectorRequestDao requestDao;

    @InjectMocks
    private GetMessageService getMessageService;

    @Test
    void getMessage_200_withAttachments() {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .recipientTaxId("FISCALCODE12345X")
                .subject("Oggetto notifica")
                .markdown("Testo notifica")
                .attachments(List.of(
                        IOConnectorRequestEntity.Attachment.builder()
                                .id("att-1")
                                .fileKey("safestorage://doc-key-1.pdf")
                                .name("allegato1.pdf")
                                .build()
                ))
                .build();

        when(requestDao.findById("REQ-001")).thenReturn(Optional.of(entity));

        GetMessageResponse response = getMessageService.getMessageDetails("REQ-001", "FISCALCODE12345X");

        assertNotNull(response);
        assertNotNull(response.getDetails());
        assertEquals("Oggetto notifica", response.getDetails().getSubject());
        assertEquals("Testo notifica", response.getDetails().getMarkdown());
        assertNotNull(response.getAttachments());
        assertEquals(1, response.getAttachments().size());
        assertEquals("allegato1.pdf", response.getAttachments().get(0).getName());
        assertEquals("application/pdf", response.getAttachments().get(0).getContentType());
        assertEquals("DOCUMENT", response.getAttachments().get(0).getCategory());
    }

    @Test
    void getMessage_200_noAttachments() {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-002")
                .recipientTaxId("FISCALCODE12345X")
                .subject("Oggetto")
                .markdown("Testo")
                .attachments(null)
                .build();

        when(requestDao.findById("REQ-002")).thenReturn(Optional.of(entity));

        GetMessageResponse response = getMessageService.getMessageDetails("REQ-002", "FISCALCODE12345X");

        assertNotNull(response);
        assertNotNull(response.getAttachments());
        assertTrue(response.getAttachments().isEmpty());
    }

    @Test
    void getMessage_200_fallbackNameToFileKey() {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-003")
                .recipientTaxId("FISCALCODE12345X")
                .subject("Oggetto")
                .markdown("Testo")
                .attachments(List.of(
                        IOConnectorRequestEntity.Attachment.builder()
                                .id("att-2")
                                .fileKey("safestorage://doc-key-2.pdf")
                                .name(null)
                                .build()
                ))
                .build();

        when(requestDao.findById("REQ-003")).thenReturn(Optional.of(entity));

        GetMessageResponse response = getMessageService.getMessageDetails("REQ-003", "FISCALCODE12345X");

        assertNotNull(response);
        assertEquals(1, response.getAttachments().size());
        assertEquals("safestorage://doc-key-2.pdf", response.getAttachments().get(0).getName());
    }

    @Test
    void getMessage_200_nullFileKey() {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-005")
                .recipientTaxId("FISCALCODE12345X")
                .subject("Oggetto")
                .markdown("Testo")
                .attachments(List.of(
                        IOConnectorRequestEntity.Attachment.builder()
                                .id("att-3")
                                .fileKey(null)
                                .name("nome-allegato.pdf")
                                .build()
                ))
                .build();

        when(requestDao.findById("REQ-005")).thenReturn(Optional.of(entity));

        GetMessageResponse response = getMessageService.getMessageDetails("REQ-005", "FISCALCODE12345X");

        assertNotNull(response);
        assertEquals(1, response.getAttachments().size());
        assertNotNull(response.getAttachments().get(0).getName());
        assertNull(response.getAttachments().get(0).getUrl());
    }

    @Test
    void getMessage_404_fiscalCodeMismatch() {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-004")
                .recipientTaxId("FISCALCODE12345X")
                .subject("Oggetto")
                .markdown("Testo")
                .build();

        when(requestDao.findById("REQ-004")).thenReturn(Optional.of(entity));

        assertThrows(PnIoGetMessageNotFoundException.class,
                () -> getMessageService.getMessageDetails("REQ-004", "DIFFERENT_CODE_X"));
    }

    @Test
    void getMessage_404_notFound() {
        when(requestDao.findById("REQ-999")).thenReturn(Optional.empty());

        assertThrows(PnIoGetMessageNotFoundException.class,
                () -> getMessageService.getMessageDetails("REQ-999", "FISCALCODE12345X"));
    }

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void getMessageDetails_setsMdcRequestIdAndIun() {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .iun("IUN-001")
                .recipientTaxId("FISCALCODE12345X")
                .subject("Oggetto")
                .markdown("Testo")
                .build();
        AtomicReference<Map<String, String>> atEntry = new AtomicReference<>();
        when(requestDao.findById("REQ-001"))
                .thenAnswer(MdcTestSupport.capturingInto(atEntry, Optional.of(entity)));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(GetMessageService.class)) {
            getMessageService.getMessageDetails("REQ-001", "FISCALCODE12345X");

            assertThat(atEntry.get())
                    .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-001")
                    .doesNotContainKey(MDCUtils.MDC_PN_IUN_KEY);
            assertThat(logs.mdcAt("Ending process"))
                    .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-001")
                    .containsEntry(MDCUtils.MDC_PN_IUN_KEY, "IUN-001");
        }
    }

    @Test
    void getMessageDetails_notFound_setsOnlyRequestId() {
        when(requestDao.findById("REQ-404")).thenReturn(Optional.empty());

        try (LogEventCaptor logs = LogEventCaptor.attachTo(GetMessageService.class)) {
            assertThatThrownBy(() -> getMessageService.getMessageDetails("REQ-404", "FISCALCODE12345X"))
                    .isInstanceOf(PnIoGetMessageNotFoundException.class);

            assertThat(logs.mdcAt("Ending process"))
                    .containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-404")
                    .doesNotContainKey(MDCUtils.MDC_PN_IUN_KEY);
        }
    }

    @Test
    void getMessageDetails_cleansAddedMdcKeys() {
        MDC.put(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .iun("IUN-001")
                .recipientTaxId("FISCALCODE12345X")
                .subject("Oggetto")
                .markdown("Testo")
                .build();
        when(requestDao.findById("REQ-001")).thenReturn(Optional.of(entity));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(GetMessageService.class)) {
            getMessageService.getMessageDetails("REQ-001", "FISCALCODE12345X");

            assertThat(logs.mdcAt("Ending process")).containsKey(MDCUtils.MDC_PN_CTX_REQUEST_ID);
        }
        assertThat(MdcTestSupport.snapshot())
                .doesNotContainKeys(MDCUtils.MDC_PN_CTX_REQUEST_ID, MDCUtils.MDC_PN_IUN_KEY)
                .containsEntry(MDCUtils.MDC_TRACE_ID_KEY, "filter-trace");
    }

    @Test
    void getMessageDetails_startingProcessLogCarriesRequestId() {
        IOConnectorRequestEntity entity = IOConnectorRequestEntity.builder()
                .requestId("REQ-001")
                .iun("IUN-001")
                .recipientTaxId("FISCALCODE12345X")
                .subject("Oggetto")
                .markdown("Testo")
                .build();
        when(requestDao.findById("REQ-001")).thenReturn(Optional.of(entity));

        try (LogEventCaptor logs = LogEventCaptor.attachTo(GetMessageService.class)) {
            getMessageService.getMessageDetails("REQ-001", "FISCALCODE12345X");

            assertThat(logs.mdcAt("Starting process")).containsEntry(MDCUtils.MDC_PN_CTX_REQUEST_ID, "REQ-001");
        }
    }
}
