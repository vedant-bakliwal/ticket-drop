package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.QueueStatus;
import com.vedantbakliwal.ticketdrop.core.WaitingRoom;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Behaviour every WaitingRoom must have. Run against the in-memory and the Redis implementation. */
abstract class WaitingRoomContract {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    static final Duration ACCESS = Duration.ofMinutes(10);

    abstract WaitingRoom room();

    String event() {
        return "e-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void firstComeFirstServed() {
        String e = event();
        for (int i = 0; i < 5; i++) room().join(e, "u" + i, NOW);
        assertThat(room().status(e, "u3", NOW)).isEqualTo(QueueStatus.queued(3));
        assertThat(room().admit(e, 2, 100, ACCESS, NOW)).isEqualTo(2);
        assertThat(room().isAdmitted(e, "u0", NOW)).isTrue();
        assertThat(room().isAdmitted(e, "u2", NOW)).isFalse();
        assertThat(room().status(e, "u3", NOW)).isEqualTo(QueueStatus.queued(1));
    }

    @Test
    void joiningAgainKeepsYourPlace() {
        String e = event();
        room().join(e, "a", NOW);
        room().join(e, "b", NOW);
        assertThat(room().join(e, "b", NOW)).isEqualTo(QueueStatus.queued(1));
        assertThat(room().queued(e)).isEqualTo(2);
    }

    @Test
    void neverExceedsTheActiveShopperCap() {
        String e = event();
        for (int i = 0; i < 10; i++) room().join(e, "u" + i, NOW);
        assertThat(room().admit(e, 5, 3, ACCESS, NOW)).isEqualTo(3);
        assertThat(room().admit(e, 5, 3, ACCESS, NOW.plusSeconds(1))).isZero();
        assertThat(room().active(e, NOW.plusSeconds(1))).isEqualTo(3);
        // when access expires, slots free up for the next buyers
        assertThat(room().admit(e, 5, 3, ACCESS, NOW.plus(ACCESS))).isEqualTo(3);
    }

    @Test
    void admittedBuyerWhoRejoinsStaysAdmitted() {
        String e = event();
        room().join(e, "a", NOW);
        room().admit(e, 1, 10, ACCESS, NOW);
        assertThat(room().join(e, "a", NOW.plusSeconds(5)).state()).isEqualTo(QueueStatus.State.ADMITTED);
        assertThat(room().status(e, "nobody", NOW).state()).isEqualTo(QueueStatus.State.NOT_IN_QUEUE);
    }
}
