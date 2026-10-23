package com.vedantbakliwal.ticketdrop.core;

/** Payment provider boundary. The project ships a fake with configurable failures and latency. */
public interface PaymentGateway {

    /** Charges and returns a payment id, or throws {@link PaymentDeclinedException}. */
    String charge(String idempotencyKey, long amountCents);

    void refund(String paymentId);
}
