package com.vedantbakliwal.ticketdrop.memory;

import com.vedantbakliwal.ticketdrop.core.PaymentDeclinedException;
import com.vedantbakliwal.ticketdrop.core.PaymentGateway;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand-in payment provider with failure injection: a decline rate and added latency,
 * changeable at runtime (see the /admin/chaos endpoint) to test checkout under failure.
 */
public class FakePaymentGateway implements PaymentGateway {

    private volatile double declineRate;
    private volatile long latencyMs;
    private final Set<String> charged = ConcurrentHashMap.newKeySet();
    private final AtomicLong charges = new AtomicLong();
    private final AtomicLong refunds = new AtomicLong();

    public FakePaymentGateway(double declineRate, long latencyMs) {
        configure(declineRate, latencyMs);
    }

    public void configure(double declineRate, long latencyMs) {
        if (declineRate < 0 || declineRate > 1 || latencyMs < 0) {
            throw new IllegalArgumentException("declineRate must be 0..1 and latency non-negative");
        }
        this.declineRate = declineRate;
        this.latencyMs = latencyMs;
    }

    @Override
    public String charge(String idempotencyKey, long amountCents) {
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < declineRate) {
            throw new PaymentDeclinedException("card declined (injected)");
        }
        if (!charged.add(idempotencyKey)) {
            throw new IllegalStateException("Double charge for key " + idempotencyKey); // must never happen
        }
        charges.incrementAndGet();
        return "pay_" + UUID.randomUUID();
    }

    @Override
    public void refund(String paymentId) {
        refunds.incrementAndGet();
    }

    public long charges() {
        return charges.get();
    }

    public long refunds() {
        return refunds.get();
    }

    public double declineRate() {
        return declineRate;
    }

    public long latencyMs() {
        return latencyMs;
    }
}
