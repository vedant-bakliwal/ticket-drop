package com.vedantbakliwal.ticketdrop.core;

import java.util.Optional;

/** Orders keyed by idempotency key. Implementations: in-memory and JDBC (H2 or PostgreSQL). */
public interface OrderRepository {

    /** Inserts a PENDING order. False if an order with this idempotency key already exists. */
    boolean insertPending(Order order);

    Optional<Order> findByKey(String idempotencyKey);

    void markCompleted(String idempotencyKey, String orderId);

    void markFailed(String idempotencyKey, String reason);

    long countCompleted(String eventId);
}
