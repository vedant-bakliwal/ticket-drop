package com.vedantbakliwal.ticketdrop.core;

import java.time.Duration;
import java.time.Instant;

/** FIFO virtual waiting room. Implementations: in-memory and Redis. */
public interface WaitingRoom {

    /** Joins the queue. Joining again returns the existing place instead of a new one. */
    QueueStatus join(String eventId, String userId, Instant now);

    QueueStatus status(String eventId, String userId, Instant now);

    /**
     * Lets up to {@code max} buyers in from the head of the queue, never exceeding
     * {@code maxActive} unexpired admissions. Returns how many were admitted.
     */
    int admit(String eventId, int max, int maxActive, Duration accessTtl, Instant now);

    boolean isAdmitted(String eventId, String userId, Instant now);

    long queued(String eventId);

    long active(String eventId, Instant now);

    /** Frees a buyer's shopper slot early (they bought, or seats ran out) so the next buyer can be admitted. */
    void leave(String eventId, String userId);
}
