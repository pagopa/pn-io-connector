package it.pagopa.pn.ioconnector.springbootcfg;

import it.pagopa.pn.common.rest.error.v1.dto.Problem;
import it.pagopa.pn.commons.exceptions.ExceptionHelper;
import it.pagopa.pn.commons.exceptions.PnExceptionsCodes;
import it.pagopa.pn.commons.exceptions.PnResponseEntityExceptionHandler;
import it.pagopa.pn.commons.exceptions.PnRuntimeException;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(-2)
@Configuration
@ControllerAdvice
@Import(ExceptionHelper.class)
public class PnErrorWebExceptionHandlerActivation extends PnResponseEntityExceptionHandler {

    public PnErrorWebExceptionHandlerActivation(ExceptionHelper exceptionHelper) {
        super(exceptionHelper);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Problem> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        return handleRuntimeException(new PnRuntimeException("Invalid parameter value",
                "Invalid value for parameter " + ex.getName(), HttpStatus.BAD_REQUEST.value(),
                PnExceptionsCodes.ERROR_CODE_PN_GENERIC_INVALIDPARAMETER, ex.getName(), "Unexpected value '" + ex.getValue() + "'", ex));
    }
}
