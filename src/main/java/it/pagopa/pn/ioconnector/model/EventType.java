package it.pagopa.pn.ioconnector.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {

    ACCEPTED(false, 10),
    SENT_TO_IO(true, 20),
    DELIVERED_TO_USER(true, 30),
    READ(true, 40),
    PAID(true, 50),
    IO_DELIVERY_FAILED(false, 60),
    SENDER_NOT_ALLOWED(true, 0),
    FAILED_TO_SEND(true, 0),
    POLLING_EXHAUSTED(false, 0),
    ATTACHMENTS_VALIDATION_FAILED(false, 0);

    private final boolean notify;
    private final int progressionRank;
}
