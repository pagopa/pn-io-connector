package it.pagopa.pn.ioconnector.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_GET_MESSAGE_NOT_FOUND;

public class PnIoGetMessageNotFoundException extends PnRuntimeException {

    public PnIoGetMessageNotFoundException(String requestId) {
        super("GET /messages/{id} Not Found", "Messaggio non trovato",
              HttpStatus.NOT_FOUND.value(),
              ERROR_CODE_IOCONNECTOR_GET_MESSAGE_NOT_FOUND,
              "requestId", requestId);
    }
}
