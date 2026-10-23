package com.vedantbakliwal.ticketdrop.core;

import java.time.Instant;

public record Hold(String holdId, String userId, int quantity, Instant expiresAt) {

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
