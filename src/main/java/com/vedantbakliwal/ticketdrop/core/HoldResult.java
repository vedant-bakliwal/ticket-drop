package com.vedantbakliwal.ticketdrop.core;

import java.time.Instant;

public record HoldResult(Outcome outcome, String holdId, int quantity, Instant expiresAt) {

    public enum Outcome { HELD, SOLD_OUT, OVER_LIMIT }

    public static HoldResult held(String holdId, int quantity, Instant expiresAt) {
        return new HoldResult(Outcome.HELD, holdId, quantity, expiresAt);
    }

    public static HoldResult rejected(Outcome outcome) {
        return new HoldResult(outcome, null, 0, null);
    }
}
