package com.vedantbakliwal.ticketdrop.store;

import com.vedantbakliwal.ticketdrop.core.Order;
import com.vedantbakliwal.ticketdrop.core.OrderRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.util.Optional;

/**
 * Orders in a relational table (H2 locally, PostgreSQL in Docker). The idempotency key is the
 * primary key, so the database itself guarantees one order per key even across instances.
 */
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcClient jdbc;

    public JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertPending(Order o) {
        try {
            jdbc.sql("""
                    INSERT INTO orders (idempotency_key, event_id, user_id, hold_id, quantity, amount_cents, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)""")
                    .params(o.idempotencyKey(), o.eventId(), o.userId(), o.holdId(), o.quantity(), o.amountCents(),
                            Timestamp.from(o.createdAt()))
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public Optional<Order> findByKey(String key) {
        return jdbc.sql("SELECT * FROM orders WHERE idempotency_key = ?").param(key)
                .query((rs, n) -> new Order(rs.getString("idempotency_key"), rs.getString("order_id"),
                        rs.getString("event_id"), rs.getString("user_id"), rs.getString("hold_id"),
                        rs.getInt("quantity"), rs.getLong("amount_cents"), Order.Status.valueOf(rs.getString("status")),
                        rs.getString("failure_reason"), rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    @Override
    public void markCompleted(String key, String orderId) {
        jdbc.sql("UPDATE orders SET status = 'COMPLETED', order_id = ? WHERE idempotency_key = ?").params(orderId, key).update();
    }

    @Override
    public void markFailed(String key, String reason) {
        jdbc.sql("UPDATE orders SET status = 'FAILED', failure_reason = ? WHERE idempotency_key = ?").params(reason, key).update();
    }

    @Override
    public long countCompleted(String eventId) {
        return jdbc.sql("SELECT COUNT(*) FROM orders WHERE event_id = ? AND status = 'COMPLETED'").param(eventId)
                .query(Long.class).single();
    }
}
