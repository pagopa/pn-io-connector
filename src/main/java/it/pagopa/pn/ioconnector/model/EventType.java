package it.pagopa.pn.ioconnector.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {

    ACCEPTED(false),
    SENDER_NOT_ALLOWED(true),
    SENT_TO_IO(true),
    DELIVERED_TO_USER(true),
    READ(true),
    PAID(true),
    POLLING_EXHAUSTED(false),
    IO_SEND_RETRY_EXHAUSTED(true);

    private final boolean notify;
}
