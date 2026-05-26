package it.pagopa.pn.ioconnector.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_GET_MESSAGE_FORBIDDEN;

public class PnIOGetMessageForbiddenException extends PnRuntimeException {

    public PnIOGetMessageForbiddenException() {
        super("GET /messages/{id} Forbidden", "Codice fiscale non coerente con il destinatario",
              HttpStatus.FORBIDDEN.value(),
              ERROR_CODE_IOCONNECTOR_GET_MESSAGE_FORBIDDEN,
              null, null);
    }
}
