package com.vedantbakliwal.ticketdrop.core;

import java.time.Instant;

/** Where a buyer stands: waiting with N people ahead, admitted until a time, or unknown. */
public record QueueStatus(State state, long ahead, Instant accessExpiresAt) {

    public enum State { QUEUED, ADMITTED, NOT_IN_QUEUE }

    public static QueueStatus queued(long ahead) {
        return new QueueStatus(State.QUEUED, ahead, null);
    }

    public static QueueStatus admitted(Instant until) {
        return new QueueStatus(State.ADMITTED, 0, until);
    }

    public static QueueStatus notInQueue() {
        return new QueueStatus(State.NOT_IN_QUEUE, 0, null);
    }
}
