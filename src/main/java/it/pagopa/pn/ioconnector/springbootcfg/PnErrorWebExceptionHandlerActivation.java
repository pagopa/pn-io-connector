package it.pagopa.pn.ioconnector.springbootcfg;

import it.pagopa.pn.commons.exceptions.ExceptionHelper;
import it.pagopa.pn.commons.exceptions.PnResponseEntityExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Order(-2)
@Configuration
@ControllerAdvice
@Import(ExceptionHelper.class)
public class PnErrorWebExceptionHandlerActivation extends PnResponseEntityExceptionHandler {

    public PnErrorWebExceptionHandlerActivation(ExceptionHelper exceptionHelper) {
        super(exceptionHelper);
    }
}
