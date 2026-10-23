package com.vedantbakliwal.ticketdrop.core;

/** The outcome of a checkout call, mapped to HTTP statuses by the web layer. */
public record CheckoutResult(Outcome outcome, Order order, String message) {

    public enum Outcome {
        CREATED,          // 201: new order
        REPLAYED,         // 200: same idempotency key, the original result
        IN_PROGRESS,      // 409: same key, first request still running
        NOT_YOUR_HOLD,    // 404: hold missing or owned by someone else
        HOLD_EXPIRED,     // 410: hold expired; any charge was refunded
        PAYMENT_DECLINED  // 402: card declined; the hold is kept until it expires
    }
}
