package com.vedantbakliwal.ticketdrop.redis;

import com.vedantbakliwal.ticketdrop.core.QueueStatus;
import com.vedantbakliwal.ticketdrop.core.WaitingRoom;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Waiting room shared by every app instance. Queue order comes from an atomic counter, and
 * join and admit run as Lua scripts so concurrent requests can't skip or double-admit anyone.
 * Keys use a {eventId} hash tag so they live on one Redis Cluster slot.
 */
public class RedisWaitingRoom implements WaitingRoom {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> JOIN = script("join.lua", List.class);
    private static final DefaultRedisScript<Long> ADMIT = script("admit.lua", Long.class);

    private final StringRedisTemplate redis;

    public RedisWaitingRoom(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static <T> DefaultRedisScript<T> script(String name, Class<T> type) {
        DefaultRedisScript<T> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource("scripts/" + name));
        s.setResultType(type);
        return s;
    }

    private static String queue(String e) { return "wr:{" + e + "}:queue"; }
    private static String admitted(String e) { return "wr:{" + e + "}:admitted"; }
    private static String seq(String e) { return "wr:{" + e + "}:seq"; }

    @Override
    @SuppressWarnings("unchecked")
    public QueueStatus join(String eventId, String userId, Instant now) {
        List<Object> r = redis.execute(JOIN, List.of(queue(eventId), admitted(eventId), seq(eventId)),
                userId, String.valueOf(now.toEpochMilli()));
        if ("ADMITTED".equals(String.valueOf(r.get(0)))) {
            return QueueStatus.admitted(Instant.ofEpochMilli((long) Double.parseDouble(String.valueOf(r.get(1)))));
        }
        return QueueStatus.queued(Long.parseLong(String.valueOf(r.get(1))));
    }

    @Override
    public QueueStatus status(String eventId, String userId, Instant now) {
        Double exp = redis.opsForZSet().score(admitted(eventId), userId);
        if (exp != null && exp > now.toEpochMilli()) {
            return QueueStatus.admitted(Instant.ofEpochMilli(exp.longValue()));
        }
        Long rank = redis.opsForZSet().rank(queue(eventId), userId);
        return rank == null ? QueueStatus.notInQueue() : QueueStatus.queued(rank);
    }

    @Override
    public int admit(String eventId, int max, int maxActive, Duration accessTtl, Instant now) {
        Long n = redis.execute(ADMIT, List.of(queue(eventId), admitted(eventId)),
                String.valueOf(max), String.valueOf(maxActive), String.valueOf(now.toEpochMilli()),
                String.valueOf(accessTtl.toMillis()));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public boolean isAdmitted(String eventId, String userId, Instant now) {
        Double exp = redis.opsForZSet().score(admitted(eventId), userId);
        return exp != null && exp > now.toEpochMilli();
    }

    @Override
    public long queued(String eventId) {
        Long n = redis.opsForZSet().zCard(queue(eventId));
        return n == null ? 0 : n;
    }

    @Override
    public long active(String eventId, Instant now) {
        Long n = redis.opsForZSet().count(admitted(eventId), now.toEpochMilli() + 1, Double.POSITIVE_INFINITY);
        return n == null ? 0 : n;
    }

    @Override
    public void leave(String eventId, String userId) {
        redis.opsForZSet().remove(admitted(eventId), userId);
    }
}
