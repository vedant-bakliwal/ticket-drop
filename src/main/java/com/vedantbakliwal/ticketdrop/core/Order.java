package com.vedantbakliwal.ticketdrop.core;

import java.time.Instant;

public record Order(String idempotencyKey, String orderId, String eventId, String userId, String holdId,
                    int quantity, long amountCents, Status status, String failureReason, Instant createdAt) {

    public enum Status { PENDING, COMPLETED, FAILED }
}
