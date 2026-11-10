package it.pagopa.pn.ioconnector.utils;

import it.pagopa.pn.commons.exceptions.PnHttpResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LogUtilsTest {

    private static final String TAX_ID = "RSSMRA80A01H501U";

    @Test
    void describeError_httpException_returnsSimpleNameAndStatusWithoutMessage() {
        PnHttpResponseException ex = new PnHttpResponseException("Error in call url=https://io/messages/" + TAX_ID, 502);

        assertThat(LogUtils.describeError(ex))
                .isEqualTo("PnHttpResponseException statusCode=502")
                .doesNotContain(TAX_ID);
    }

    @Test
    void describeError_genericException_returnsSimpleNameOnly() {
        assertThat(LogUtils.describeError(new IllegalStateException("failure for " + TAX_ID)))
                .isEqualTo("IllegalStateException");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Error in call url=https://api.io.pagopa.it/api/v1/messages/RSSMRA80A01H501U/01JC7ZQ3M0 method=GET statusCode=502|Error in call url=https://api.io.pagopa.it/api/v1/messages/***/01JC7ZQ3M0 method=GET statusCode=502",
            "I/O error on GET request for \"https://api.io.pagopa.it/api/v1/messages/RSSMRA80A01H501U/01JC7ZQ3M0\": Read timed out|I/O error on GET request for \"https://api.io.pagopa.it/api/v1/messages/***/01JC7ZQ3M0\": Read timed out",
            "Error in call url=https://api.io.pagopa.it/api/v1/messages method=POST statusCode=500|Error in call url=https://api.io.pagopa.it/api/v1/messages method=POST statusCode=500",
            "Read timed out|Read timed out"
    })
    void maskTaxIdInPath_masksFiscalCodeSegmentOfMessagesPath(String message, String expected) {
        assertThat(LogUtils.maskTaxIdInPath(message)).isEqualTo(expected);
    }

    @Test
    void maskTaxIdInPath_null_returnsNull() {
        assertThat(LogUtils.maskTaxIdInPath(null)).isNull();
    }
}
