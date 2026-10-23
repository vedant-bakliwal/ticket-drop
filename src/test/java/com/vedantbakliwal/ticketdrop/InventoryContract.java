package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.HoldResult;
import com.vedantbakliwal.ticketdrop.core.Inventory;
import com.vedantbakliwal.ticketdrop.core.InventoryStats;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Behaviour every Inventory must have. Run against the in-memory and the Redis implementation. */
abstract class InventoryContract {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final Duration TTL = Duration.ofMinutes(5);

    abstract Inventory inventory();

    String newEvent(int capacity) {
        String id = "e-" + UUID.randomUUID().toString().substring(0, 8);
        inventory().initialize(id, capacity);
        return id;
    }

    @Test
    void neverOversellsWhenManyBuyersRaceForTheLastSeats() throws Exception {
        String e = newEvent(100);
        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Future<HoldResult>> results = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            String user = "u" + i;
            results.add(pool.submit(() -> inventory().hold(e, user, 1, 4, TTL, NOW)));
        }
        int held = 0;
        for (Future<HoldResult> f : results) {
            if (f.get().outcome() == HoldResult.Outcome.HELD) held++;
        }
        pool.shutdown();
        assertThat(held).isEqualTo(100);
        InventoryStats s = inventory().stats(e);
        assertThat(s.available()).isZero();
        assertThat(s.consistent()).isTrue();
    }

    @Test
    void enforcesThePerBuyerLimitAcrossHolds() {
        String e = newEvent(50);
        assertThat(inventory().hold(e, "alice", 3, 4, TTL, NOW).outcome()).isEqualTo(HoldResult.Outcome.HELD);
        assertThat(inventory().hold(e, "alice", 2, 4, TTL, NOW).outcome()).isEqualTo(HoldResult.Outcome.OVER_LIMIT);
        assertThat(inventory().hold(e, "alice", 1, 4, TTL, NOW).outcome()).isEqualTo(HoldResult.Outcome.HELD);
    }

    @Test
    void confirmsAnUnexpiredHoldExactlyOnce() {
        String e = newEvent(10);
        HoldResult h = inventory().hold(e, "bob", 2, 4, TTL, NOW);
        assertThat(inventory().findHold(e, h.holdId())).hasValueSatisfying(x -> {
            assertThat(x.userId()).isEqualTo("bob");
            assertThat(x.quantity()).isEqualTo(2);
        });
        assertThat(inventory().confirm(e, h.holdId(), NOW.plusSeconds(60))).isTrue();
        assertThat(inventory().confirm(e, h.holdId(), NOW.plusSeconds(61))).isFalse();
        assertThat(inventory().stats(e)).isEqualTo(new InventoryStats(10, 8, 0, 2));
    }

    @Test
    void expiredHoldsCannotBeBoughtAndGoBackOnSale() {
        String e = newEvent(5);
        HoldResult h = inventory().hold(e, "carol", 4, 4, TTL, NOW);
        assertThat(inventory().confirm(e, h.holdId(), NOW.plus(TTL))).isFalse();
        assertThat(inventory().releaseExpired(e, NOW.plus(TTL))).isEqualTo(4);
        assertThat(inventory().stats(e)).isEqualTo(new InventoryStats(5, 5, 0, 0));
        // the buyer's limit is freed too
        assertThat(inventory().hold(e, "carol", 4, 4, TTL, NOW.plus(TTL)).outcome()).isEqualTo(HoldResult.Outcome.HELD);
    }

    @Test
    void reportsSoldOut() {
        String e = newEvent(2);
        inventory().hold(e, "a", 2, 4, TTL, NOW);
        assertThat(inventory().hold(e, "b", 1, 4, TTL, NOW).outcome()).isEqualTo(HoldResult.Outcome.SOLD_OUT);
    }
}
