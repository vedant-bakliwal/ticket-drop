package com.vedantbakliwal.ticketdrop.core;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key token bucket: {@code capacity} requests in a burst, refilled at {@code perSecond}.
 * Used to stop one client from hammering the join endpoint during a drop. Per instance; a
 * multi-instance deployment would move this to Redis or the API gateway.
 */
public class TokenBucket {

    private record State(double tokens, long lastNanos) { }

    private final double capacity;
    private final double perSecond;
    private final ConcurrentHashMap<String, State> buckets = new ConcurrentHashMap<>();

    public TokenBucket(double capacity, double perSecond) {
        this.capacity = capacity;
        this.perSecond = perSecond;
    }

    public boolean tryAcquire(String key, Instant now) {
        long t = now.getEpochSecond() * 1_000_000_000L + now.getNano();
        boolean[] allowed = {false};
        buckets.compute(key, (k, s) -> {
            double tokens = s == null ? capacity : Math.min(capacity, s.tokens() + (t - s.lastNanos()) / 1e9 * perSecond);
            if (tokens >= 1) {
                allowed[0] = true;
                tokens -= 1;
            }
            return new State(tokens, t);
        });
        return allowed[0];
    }
}
