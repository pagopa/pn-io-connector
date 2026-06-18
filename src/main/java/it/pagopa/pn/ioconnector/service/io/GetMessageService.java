package it.pagopa.pn.ioconnector.service.io;

import it.pagopa.pn.ioconnector.exceptions.PnIoGetMessageNotFoundException;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetMessageResponse;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetMessageResponseAttachmentsInner;
import it.pagopa.pn.ioconnector.generated.openapi.server.v1.dto.GetMessageResponseDetails;
import it.pagopa.pn.ioconnector.middleware.db.IOConnectorRequestDao;
import it.pagopa.pn.ioconnector.middleware.db.entities.IOConnectorRequestEntity;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static it.pagopa.pn.ioconnector.utils.LogUtils.GET_MESSAGE_DETAILS;

@Service
@CustomLog
@RequiredArgsConstructor
public class GetMessageService {

    private final IOConnectorRequestDao requestDao;

    public GetMessageResponse getMessageDetails(String requestId, String cxId) {
        log.logStartingProcess(GET_MESSAGE_DETAILS);
        try {
            IOConnectorRequestEntity entity = requestDao.findById(requestId)
                    .orElseThrow(() -> new PnIoGetMessageNotFoundException(requestId));

            if (!Objects.equals(cxId, entity.getRecipientTaxId())) {
                throw new PnIoGetMessageNotFoundException(requestId);
            }

            GetMessageResponse response = buildResponse(entity);
            log.logEndingProcess(GET_MESSAGE_DETAILS);
            return response;
        } catch (Exception e) {
            log.logEndingProcess(GET_MESSAGE_DETAILS, false, e.getMessage(), e);
            throw e;
        }
    }

    private GetMessageResponse buildResponse(IOConnectorRequestEntity entity) {
        GetMessageResponseDetails details = new GetMessageResponseDetails()
                .subject(entity.getSubject())
                .markdown(entity.getMarkdown());

        List<IOConnectorRequestEntity.Attachment> sourceAttachments =
                entity.getAttachments() != null ? entity.getAttachments() : new ArrayList<>();

        List<GetMessageResponseAttachmentsInner> attachments = new ArrayList<>();
        for (IOConnectorRequestEntity.Attachment attachment : sourceAttachments) {
            String name = StringUtils.hasText(attachment.getName())
                    ? attachment.getName()
                    : attachment.getFileKey();
            GetMessageResponseAttachmentsInner item = new GetMessageResponseAttachmentsInner()
                    .id(attachment.getId())
                    .name(name)
                    .contentType("application/pdf")
                    .category("DOCUMENT")
                    .url(attachment.getFileKey());
            attachments.add(item);
        }

        return new GetMessageResponse()
                .details(details)
                .attachments(attachments);
    }
}
