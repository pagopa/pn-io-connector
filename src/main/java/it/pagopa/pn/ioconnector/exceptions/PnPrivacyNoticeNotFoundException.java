package it.pagopa.pn.ioconnector.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_NOT_FOUND;

public class PnPrivacyNoticeNotFoundException extends PnRuntimeException {

    public PnPrivacyNoticeNotFoundException(String consentsType, String portalType) {
        super("Privacy notice not found", "Non e' stato trovato nessun Privacy Notice",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ERROR_CODE_IOCONNECTOR_PRIVACY_NOTICE_NOT_FOUND,
                "consentsType/portalType", consentsType + "/" + portalType);
    }
}
