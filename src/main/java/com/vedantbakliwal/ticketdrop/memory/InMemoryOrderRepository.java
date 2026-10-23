package com.vedantbakliwal.ticketdrop.memory;

import com.vedantbakliwal.ticketdrop.core.Order;
import com.vedantbakliwal.ticketdrop.core.OrderRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOrderRepository implements OrderRepository {

    private final ConcurrentHashMap<String, Order> byKey = new ConcurrentHashMap<>();

    @Override
    public boolean insertPending(Order order) {
        return byKey.putIfAbsent(order.idempotencyKey(), order) == null;
    }

    @Override
    public Optional<Order> findByKey(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    @Override
    public void markCompleted(String key, String orderId) {
        byKey.computeIfPresent(key, (k, o) -> new Order(k, orderId, o.eventId(), o.userId(), o.holdId(), o.quantity(),
                o.amountCents(), Order.Status.COMPLETED, null, o.createdAt()));
    }

    @Override
    public void markFailed(String key, String reason) {
        byKey.computeIfPresent(key, (k, o) -> new Order(k, null, o.eventId(), o.userId(), o.holdId(), o.quantity(),
                o.amountCents(), Order.Status.FAILED, reason, o.createdAt()));
    }

    @Override
    public long countCompleted(String eventId) {
        return byKey.values().stream().filter(o -> o.eventId().equals(eventId) && o.status() == Order.Status.COMPLETED).count();
    }
}
