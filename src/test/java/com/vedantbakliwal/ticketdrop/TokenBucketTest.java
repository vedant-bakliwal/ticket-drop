package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.TokenBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    @Test
    void allowsABurstThenRefillsAtTheConfiguredRate() {
        TokenBucket bucket = new TokenBucket(3, 1);
        Instant t = Instant.parse("2026-11-01T10:00:00Z");
        int allowed = 0;
        for (int i = 0; i < 10; i++) if (bucket.tryAcquire("u1", t)) allowed++;
        assertThat(allowed).isEqualTo(3);
        assertThat(bucket.tryAcquire("u1", t.plusMillis(500))).isFalse();
        assertThat(bucket.tryAcquire("u1", t.plusSeconds(1))).isTrue();
        assertThat(bucket.tryAcquire("u2", t)).isTrue(); // buckets are per key
    }
}
