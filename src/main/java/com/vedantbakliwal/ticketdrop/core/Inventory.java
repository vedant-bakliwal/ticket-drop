package com.vedantbakliwal.ticketdrop.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Seat inventory with time-boxed holds. Every method is atomic: seats are never sold or held
 * twice, and expired holds return to sale. Implementations: in-memory and Redis (Lua scripts).
 */
public interface Inventory {

    void initialize(String eventId, int capacity);

    HoldResult hold(String eventId, String userId, int quantity, int maxPerUser, Duration ttl, Instant now);

    Optional<Hold> findHold(String eventId, String holdId);

    /** Turns an unexpired hold into a sale. False if the hold is gone or expired. */
    boolean confirm(String eventId, String holdId, Instant now);

    /** Returns expired holds' seats to sale. Returns the number of seats released. */
    int releaseExpired(String eventId, Instant now);

    InventoryStats stats(String eventId);
}
