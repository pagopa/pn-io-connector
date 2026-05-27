package it.pagopa.pn.ioconnector.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_GET_USER_PROFILE_ERROR;


public class PnIoGetProfileException extends PnRuntimeException {

    public PnIoGetProfileException(int statusCode, String detail) {
        super(
                "POST/profile Error", "Errore in fase di POST/profile su IO", statusCode, ERROR_CODE_IOCONNECTOR_GET_USER_PROFILE_ERROR, "POST/profile", detail
        );
    }

}
