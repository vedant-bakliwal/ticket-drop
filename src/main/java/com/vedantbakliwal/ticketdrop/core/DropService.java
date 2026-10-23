package com.vedantbakliwal.ticketdrop.core;

import java.time.Clock;
import java.time.Instant;

/** The buyer-facing flow: join the queue, wait to be admitted, hold seats. */
public class DropService {

    public enum JoinOutcome { OK, RATE_LIMITED }

    public record JoinResult(JoinOutcome outcome, QueueStatus status) { }

    public enum HoldOutcome { OK, NOT_ADMITTED, INVALID_QUANTITY }

    public record HoldAttempt(HoldOutcome outcome, HoldResult result) { }

    private final WaitingRoom room;
    private final Inventory inventory;
    private final TokenBucket joinLimiter;
    private final Clock clock;

    public DropService(WaitingRoom room, Inventory inventory, TokenBucket joinLimiter, Clock clock) {
        this.room = room;
        this.inventory = inventory;
        this.joinLimiter = joinLimiter;
        this.clock = clock;
    }

    public JoinResult join(EventConfig event, String userId, String clientKey) {
        Instant now = clock.instant();
        if (!joinLimiter.tryAcquire(clientKey, now)) {
            return new JoinResult(JoinOutcome.RATE_LIMITED, null);
        }
        return new JoinResult(JoinOutcome.OK, room.join(event.eventId(), userId, now));
    }

    public QueueStatus status(EventConfig event, String userId) {
        return room.status(event.eventId(), userId, clock.instant());
    }

    public HoldAttempt hold(EventConfig event, String userId, int quantity) {
        Instant now = clock.instant();
        if (quantity < 1 || quantity > event.maxPerUser()) {
            return new HoldAttempt(HoldOutcome.INVALID_QUANTITY, null);
        }
        if (!room.isAdmitted(event.eventId(), userId, now)) {
            return new HoldAttempt(HoldOutcome.NOT_ADMITTED, null);
        }
        return new HoldAttempt(HoldOutcome.OK,
                inventory.hold(event.eventId(), userId, quantity, event.maxPerUser(), event.holdTtl(), now));
    }

    /** One scheduler tick: release expired holds, then admit the next buyers. */
    public int tick(EventConfig event) {
        Instant now = clock.instant();
        inventory.releaseExpired(event.eventId(), now);
        return room.admit(event.eventId(), event.admitPerTick(), event.maxActiveShoppers(), event.accessTtl(), now);
    }
}
