package it.pagopa.pn.ioconnector.exceptions;

import it.pagopa.pn.commons.exceptions.PnRuntimeException;

import static it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes.*;

public class PnDataVaultException extends PnRuntimeException {

    public PnDataVaultException(int statusCode, String detail) {
        super(
            "DataVault Error", "Errore chiamata a DataVault", statusCode, ERROR_CODE_IOCONNECTOR_DATAVAULT_DEANONYMIZE_ERROR, "DataVault Deanonymize", detail
        );
    }

}
