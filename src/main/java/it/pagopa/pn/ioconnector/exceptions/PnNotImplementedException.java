package it.pagopa.pn.ioconnector.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.http.HttpStatus;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_NOT_IMPLEMENTED;

//TODO: Exception temporanea per service non ancora implementati - rimuovere quando tutti i flussi saranno completi
public class PnNotImplementedException extends PnRuntimeException {

    public PnNotImplementedException(String operation) {
        super("Operation not implemented", "Operazione non ancora implementata",
              HttpStatus.NOT_IMPLEMENTED.value(),
              ERROR_CODE_IOCONNECTOR_NOT_IMPLEMENTED,
              "operation", operation);
    }
}
