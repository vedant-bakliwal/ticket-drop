package com.vedantbakliwal.ticketdrop.redis;

import com.vedantbakliwal.ticketdrop.core.Hold;
import com.vedantbakliwal.ticketdrop.core.HoldResult;
import com.vedantbakliwal.ticketdrop.core.Inventory;
import com.vedantbakliwal.ticketdrop.core.InventoryStats;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.vedantbakliwal.ticketdrop.redis.RedisWaitingRoom.script;

/**
 * Seat inventory in Redis. Each state change is one Lua script, so the check ("are there
 * enough seats?") and the update happen atomically even with many app instances: the
 * classic check-then-act race that oversells tickets cannot happen.
 */
public class RedisInventory implements Inventory {

    private static final DefaultRedisScript<String> HOLD = script("hold.lua", String.class);
    private static final DefaultRedisScript<Long> CONFIRM = script("confirm.lua", Long.class);
    private static final DefaultRedisScript<Long> RELEASE = script("release.lua", Long.class);
    private static final DefaultRedisScript<Long> INIT = script("init.lua", Long.class);

    private final StringRedisTemplate redis;

    public RedisInventory(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String e, String name) { return "inv:{" + e + "}:" + name; }

    /** Sets up the event only if it does not exist yet, so restarting an instance never resets a live sale. */
    @Override
    public void initialize(String eventId, int capacity) {
        redis.execute(INIT, List.of(key(eventId, "capacity"), key(eventId, "available"), key(eventId, "sold")),
                String.valueOf(capacity));
    }

    @Override
    public HoldResult hold(String eventId, String userId, int quantity, int maxPerUser, Duration ttl, Instant now) {
        String holdId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(ttl);
        String outcome = redis.execute(HOLD,
                List.of(key(eventId, "available"), key(eventId, "users"), key(eventId, "holds"), key(eventId, "expiry")),
                userId, String.valueOf(quantity), String.valueOf(maxPerUser), holdId, String.valueOf(expiresAt.toEpochMilli()));
        return switch (String.valueOf(outcome)) {
            case "HELD" -> HoldResult.held(holdId, quantity, expiresAt);
            case "OVER_LIMIT" -> HoldResult.rejected(HoldResult.Outcome.OVER_LIMIT);
            default -> HoldResult.rejected(HoldResult.Outcome.SOLD_OUT);
        };
    }

    @Override
    public Optional<Hold> findHold(String eventId, String holdId) {
        Object v = redis.opsForHash().get(key(eventId, "holds"), holdId);
        return v == null ? Optional.empty() : Optional.of(parse(holdId, v.toString()));
    }

    private static Hold parse(String holdId, String value) {
        int b = value.lastIndexOf('|');
        int a = value.lastIndexOf('|', b - 1);
        return new Hold(holdId, value.substring(0, a), Integer.parseInt(value.substring(a + 1, b)),
                Instant.ofEpochMilli(Long.parseLong(value.substring(b + 1))));
    }

    @Override
    public boolean confirm(String eventId, String holdId, Instant now) {
        Long r = redis.execute(CONFIRM, List.of(key(eventId, "holds"), key(eventId, "expiry"), key(eventId, "sold")),
                holdId, String.valueOf(now.toEpochMilli()));
        return r != null && r == 1L;
    }

    @Override
    public int releaseExpired(String eventId, Instant now) {
        Long r = redis.execute(RELEASE,
                List.of(key(eventId, "holds"), key(eventId, "expiry"), key(eventId, "available"), key(eventId, "users")),
                String.valueOf(now.toEpochMilli()));
        return r == null ? 0 : r.intValue();
    }

    @Override
    public InventoryStats stats(String eventId) {
        int held = redis.opsForHash().values(key(eventId, "holds")).stream()
                .mapToInt(v -> parse("-", v.toString()).quantity()).sum();
        return new InventoryStats(intValue(key(eventId, "capacity")), intValue(key(eventId, "available")), held,
                intValue(key(eventId, "sold")));
    }

    private int intValue(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0 : Integer.parseInt(v);
    }
}
