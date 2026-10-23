package com.vedantbakliwal.ticketdrop.memory;

import com.vedantbakliwal.ticketdrop.core.Hold;
import com.vedantbakliwal.ticketdrop.core.HoldResult;
import com.vedantbakliwal.ticketdrop.core.Inventory;
import com.vedantbakliwal.ticketdrop.core.InventoryStats;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Single-instance inventory. A lock per call gives the same atomicity the Redis Lua scripts give. */
public class InMemoryInventory implements Inventory {

    private static final class Stock {
        int capacity;
        int available;
        int sold;
        final Map<String, Hold> holds = new HashMap<>();
        final Map<String, Integer> perUser = new HashMap<>(); // seats held or bought by each buyer
    }

    private final Map<String, Stock> events = new HashMap<>();

    private Stock stock(String eventId) {
        Stock s = events.get(eventId);
        if (s == null) {
            throw new IllegalArgumentException("Unknown event " + eventId);
        }
        return s;
    }

    @Override
    public synchronized void initialize(String eventId, int capacity) {
        Stock s = new Stock();
        s.capacity = capacity;
        s.available = capacity;
        events.put(eventId, s);
    }

    @Override
    public synchronized HoldResult hold(String eventId, String userId, int quantity, int maxPerUser, Duration ttl, Instant now) {
        Stock s = stock(eventId);
        int mine = s.perUser.getOrDefault(userId, 0);
        if (mine + quantity > maxPerUser) {
            return HoldResult.rejected(HoldResult.Outcome.OVER_LIMIT);
        }
        if (s.available < quantity) {
            return HoldResult.rejected(HoldResult.Outcome.SOLD_OUT);
        }
        s.available -= quantity;
        s.perUser.put(userId, mine + quantity);
        Hold h = new Hold(UUID.randomUUID().toString(), userId, quantity, now.plus(ttl));
        s.holds.put(h.holdId(), h);
        return HoldResult.held(h.holdId(), quantity, h.expiresAt());
    }

    @Override
    public synchronized Optional<Hold> findHold(String eventId, String holdId) {
        return Optional.ofNullable(stock(eventId).holds.get(holdId));
    }

    @Override
    public synchronized boolean confirm(String eventId, String holdId, Instant now) {
        Stock s = stock(eventId);
        Hold h = s.holds.get(holdId);
        if (h == null || h.expired(now)) {
            return false;
        }
        s.holds.remove(holdId);
        s.sold += h.quantity();
        return true;
    }

    @Override
    public synchronized int releaseExpired(String eventId, Instant now) {
        Stock s = stock(eventId);
        int released = 0;
        var it = s.holds.values().iterator();
        while (it.hasNext()) {
            Hold h = it.next();
            if (h.expired(now)) {
                it.remove();
                s.available += h.quantity();
                s.perUser.merge(h.userId(), -h.quantity(), Integer::sum);
                released += h.quantity();
            }
        }
        return released;
    }

    @Override
    public synchronized InventoryStats stats(String eventId) {
        Stock s = stock(eventId);
        int held = s.holds.values().stream().mapToInt(Hold::quantity).sum();
        return new InventoryStats(s.capacity, s.available, held, s.sold);
    }
}
