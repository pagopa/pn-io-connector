package it.pagopa.pn.ioconnector.model.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageStatusValue {

    ACCEPTED("ACCEPTED"),
    THROTTLED("THROTTLED"),
    FAILED("FAILED"),
    PROCESSED("PROCESSED"),
    REJECTED("REJECTED");

    private final String value;

    MessageStatusValue(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @JsonCreator
    public static MessageStatusValue fromValue(String value) {
        for (MessageStatusValue b : MessageStatusValue.values()) {
            if (b.value.equalsIgnoreCase(value)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
}
