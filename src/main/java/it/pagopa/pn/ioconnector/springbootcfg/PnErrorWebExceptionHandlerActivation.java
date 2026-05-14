package it.pagopa.pn.ioconnector.springbootcfg;

import it.pagopa.pn.commons.exceptions.ExceptionHelper;
import it.pagopa.pn.commons.exceptions.PnResponseEntityExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;

@Configuration
@Order(-2)
@Import(ExceptionHelper.class)
public class PnErrorWebExceptionHandlerActivation extends PnResponseEntityExceptionHandler {

    public PnErrorWebExceptionHandlerActivation(ExceptionHelper exceptionHelper) {
        super(exceptionHelper);
    }
}
