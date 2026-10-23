package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.CheckoutResult;
import com.vedantbakliwal.ticketdrop.core.CheckoutService;
import com.vedantbakliwal.ticketdrop.core.EventConfig;
import com.vedantbakliwal.ticketdrop.core.HoldResult;
import com.vedantbakliwal.ticketdrop.memory.FakePaymentGateway;
import com.vedantbakliwal.ticketdrop.memory.InMemoryInventory;
import com.vedantbakliwal.ticketdrop.memory.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutServiceTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final EventConfig EVENT = new EventConfig("drop", 100, 4, Duration.ofMinutes(5), Duration.ofMinutes(10), 50, 500, 7500);

    final InMemoryInventory inventory = new InMemoryInventory();
    final InMemoryOrderRepository orders = new InMemoryOrderRepository();

    CheckoutServiceTest() {
        inventory.initialize("drop", 100);
    }

    HoldResult hold(String user, int qty) {
        return inventory.hold("drop", user, qty, 4, EVENT.holdTtl(), NOW);
    }

    @Test
    void concurrentDoubleSubmitChargesOnce() throws Exception {
        FakePaymentGateway pay = new FakePaymentGateway(0, 25);
        CheckoutService checkout = new CheckoutService(inventory, orders, pay, Clock.fixed(NOW, ZoneOffset.UTC));
        HoldResult h = hold("bob", 2);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<CheckoutResult>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> checkout.checkout(EVENT, "bob", h.holdId(), "key-0001")));
        }
        long created = 0;
        for (Future<CheckoutResult> f : results) {
            if (f.get().outcome() == CheckoutResult.Outcome.CREATED) created++;
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(pay.charges()).isEqualTo(1);
        assertThat(checkout.checkout(EVENT, "bob", h.holdId(), "key-0001").outcome()).isEqualTo(CheckoutResult.Outcome.REPLAYED);
        assertThat(inventory.stats("drop").sold()).isEqualTo(2);
    }

    @Test
    void refundsWhenTheHoldExpiresDuringPayment() {
        Instant[] now = {NOW};
        Clock moving = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId z) { return this; }
            public Instant instant() { return now[0]; }
        };
        FakePaymentGateway slow = new FakePaymentGateway(0, 0) {
            @Override
            public String charge(String key, long cents) {
                now[0] = NOW.plus(Duration.ofMinutes(6)); // payment took longer than the hold
                return super.charge(key, cents);
            }
        };
        CheckoutService checkout = new CheckoutService(inventory, orders, slow, moving);
        CheckoutResult r = checkout.checkout(EVENT, "carol", hold("carol", 1).holdId(), "key-0002");
        assertThat(r.outcome()).isEqualTo(CheckoutResult.Outcome.HOLD_EXPIRED);
        assertThat(slow.refunds()).isEqualTo(1);
        assertThat(inventory.stats("drop").sold()).isZero();
    }

    @Test
    void declinedPaymentKeepsTheHoldAndRecordsTheFailure() {
        CheckoutService checkout = new CheckoutService(inventory, orders, new FakePaymentGateway(1.0, 0), Clock.fixed(NOW, ZoneOffset.UTC));
        HoldResult h = hold("dave", 1);
        assertThat(checkout.checkout(EVENT, "dave", h.holdId(), "key-0003").outcome()).isEqualTo(CheckoutResult.Outcome.PAYMENT_DECLINED);
        assertThat(inventory.findHold("drop", h.holdId())).isPresent();
        assertThat(checkout.checkout(EVENT, "dave", h.holdId(), "key-0003").order().failureReason()).contains("declined");
    }

    @Test
    void cannotBuySomeoneElsesHold() {
        CheckoutService checkout = new CheckoutService(inventory, orders, new FakePaymentGateway(0, 0), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(checkout.checkout(EVENT, "eve", hold("frank", 1).holdId(), "key-0004").outcome())
                .isEqualTo(CheckoutResult.Outcome.NOT_YOUR_HOLD);
    }
}
