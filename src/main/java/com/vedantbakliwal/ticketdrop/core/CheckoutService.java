package com.vedantbakliwal.ticketdrop.core;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a seat hold into an order, exactly once per idempotency key.
 *
 * <ol>
 *   <li>Claim the idempotency key by inserting a PENDING order (unique key). A duplicate or
 *       concurrent request with the same key gets the stored result or "in progress", so a
 *       double-clicked buy button or a client retry never charges twice.</li>
 *   <li>Charge the card.</li>
 *   <li>Confirm the hold. If it expired while the payment ran, refund (compensation).</li>
 * </ol>
 */
public class CheckoutService {

    private final Inventory inventory;
    private final OrderRepository orders;
    private final PaymentGateway payments;
    private final Clock clock;

    public CheckoutService(Inventory inventory, OrderRepository orders, PaymentGateway payments, Clock clock) {
        this.inventory = inventory;
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
    }

    public CheckoutResult checkout(EventConfig event, String userId, String holdId, String idempotencyKey) {
        Instant now = clock.instant();
        Optional<Order> existing = orders.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get());
        }

        Optional<Hold> hold = inventory.findHold(event.eventId(), holdId).filter(h -> h.userId().equals(userId));
        if (hold.isEmpty()) {
            return new CheckoutResult(CheckoutResult.Outcome.NOT_YOUR_HOLD, null, "No such hold for this buyer");
        }
        if (hold.get().expired(now)) {
            return new CheckoutResult(CheckoutResult.Outcome.HOLD_EXPIRED, null, "The hold expired; join again");
        }

        int qty = hold.get().quantity();
        Order pending = new Order(idempotencyKey, null, event.eventId(), userId, holdId, qty,
                qty * event.priceCents(), Order.Status.PENDING, null, now);
        if (!orders.insertPending(pending)) {
            return replay(orders.findByKey(idempotencyKey).orElseThrow());
        }

        String paymentId;
        try {
            paymentId = payments.charge(idempotencyKey, pending.amountCents());
        } catch (PaymentDeclinedException e) {
            orders.markFailed(idempotencyKey, "payment declined: " + e.getMessage());
            return new CheckoutResult(CheckoutResult.Outcome.PAYMENT_DECLINED, orders.findByKey(idempotencyKey).orElseThrow(),
                    "Payment declined. The seats stay held until the hold expires; retry with a new idempotency key.");
        }

        if (!inventory.confirm(event.eventId(), holdId, clock.instant())) {
            payments.refund(paymentId);
            orders.markFailed(idempotencyKey, "hold expired during payment; refunded");
            return new CheckoutResult(CheckoutResult.Outcome.HOLD_EXPIRED, orders.findByKey(idempotencyKey).orElseThrow(),
                    "The hold expired during payment and the charge was refunded");
        }

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        orders.markCompleted(idempotencyKey, orderId);
        return new CheckoutResult(CheckoutResult.Outcome.CREATED, orders.findByKey(idempotencyKey).orElseThrow(), "Order placed");
    }

    private static CheckoutResult replay(Order order) {
        return switch (order.status()) {
            case PENDING -> new CheckoutResult(CheckoutResult.Outcome.IN_PROGRESS, order, "A request with this key is still running");
            case COMPLETED -> new CheckoutResult(CheckoutResult.Outcome.REPLAYED, order, "Same key: original order returned");
            case FAILED -> new CheckoutResult(CheckoutResult.Outcome.REPLAYED, order, "Same key: original failure returned");
        };
    }
}
