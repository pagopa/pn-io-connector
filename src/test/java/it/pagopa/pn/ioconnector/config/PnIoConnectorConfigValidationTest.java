package it.pagopa.pn.ioconnector.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PnIoConnectorConfigValidationTest {

    private Validator validator;
    private PnIoConnectorConfig config;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        config = new PnIoConnectorConfig();
        config.setSqsSendQueueName("queue");
        config.setOneTrustBaseUrl("http://onetrust.test");
        config.setUserAttributesBaseUrl("http://user-attributes.test");
    }

    private Set<String> violatedProperties() {
        return validator.validate(config).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validate_allRequiredPropertiesPresent_noViolations() {
        assertThat(violatedProperties()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void validate_blankOneTrustBaseUrl_isViolation(String value) {
        config.setOneTrustBaseUrl(value);

        assertThat(violatedProperties()).containsExactly("oneTrustBaseUrl");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void validate_blankUserAttributesBaseUrl_isViolation(String value) {
        config.setUserAttributesBaseUrl(value);

        assertThat(violatedProperties()).containsExactly("userAttributesBaseUrl");
    }
}
