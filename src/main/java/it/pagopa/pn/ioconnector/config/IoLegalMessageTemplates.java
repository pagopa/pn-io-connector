package it.pagopa.pn.ioconnector.config;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.ioconnector.exceptions.PnIoConnectorExceptionCodes;
import lombok.CustomLog;
import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


@Getter
@CustomLog
@Component
public class IoLegalMessageTemplates {

    static final String TEMPLATES_PATH = "appio_message_templates/";

    static final String MARKDOWN_COURTESY_IT = "markdown_upgrade_app_io_message_IT.md";
    static final String MARKDOWN_COURTESY_EN = "markdown_upgrade_app_io_message_EN.md";
    static final String MARKDOWN_OPTIN_ANALOG = "markdown_activation_app_io_analog_message.md";
    static final String MARKDOWN_OPTIN_DIGITAL = "markdown_activation_app_io_digital_message.md";
    static final String SUBJECT_OPTIN = "subject_activation_app_io_message.md";
    static final String SUBJECT_COURTESY = "subject_courtesy_app_io_message.md";

    private final String markdownCourtesyIt;
    private final String markdownCourtesyEn;
    private final String markdownOptInAnalog;
    private final String markdownOptInDigital;
    private final String subjectOptIn;
    private final String subjectCourtesy;

    public IoLegalMessageTemplates() {
        this(TEMPLATES_PATH);
    }

    IoLegalMessageTemplates(String basePath) {
        this.markdownCourtesyIt = load(basePath, MARKDOWN_COURTESY_IT);
        this.markdownCourtesyEn = load(basePath, MARKDOWN_COURTESY_EN);
        this.markdownOptInAnalog = load(basePath, MARKDOWN_OPTIN_ANALOG);
        this.markdownOptInDigital = load(basePath, MARKDOWN_OPTIN_DIGITAL);
        this.subjectOptIn = load(basePath, SUBJECT_OPTIN);
        this.subjectCourtesy = load(basePath, SUBJECT_COURTESY);
        log.info("IO legal message templates loaded from classpath:{}", basePath);
    }

    private static String load(String basePath, String filename) {
        try (InputStream in = new ClassPathResource(basePath + filename).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PnInternalException("Cannot load IO message template: " + basePath + filename,
                    PnIoConnectorExceptionCodes.ERROR_CODE_IOCONNECTOR_MISSING_TEMPLATE, e);
        }
    }
}
