package it.pagopa.pn.ioconnector.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_GET_USER_PROFILE_ERROR;


public class PnIOGetProfileException extends PnRuntimeException {

    public PnIOGetProfileException(int statusCode, String detail) {
        super(
            "GET/profile Error", "Errore in fase di GET/profile su IO", statusCode, ERROR_CODE_IOCONNECTOR_GET_USER_PROFILE_ERROR, "GET/profile", detail
        );
    }

}
