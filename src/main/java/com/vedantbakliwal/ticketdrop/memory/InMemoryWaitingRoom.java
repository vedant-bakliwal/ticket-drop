package com.vedantbakliwal.ticketdrop.memory;

import com.vedantbakliwal.ticketdrop.core.QueueStatus;
import com.vedantbakliwal.ticketdrop.core.WaitingRoom;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;

/** Single-instance waiting room. One lock keeps join, admit and status consistent. */
public class InMemoryWaitingRoom implements WaitingRoom {

    private static final class Room {
        final LinkedHashSet<String> queue = new LinkedHashSet<>();   // insertion order = FIFO
        final Map<String, Instant> admitted = new HashMap<>();       // user -> access expiry
    }

    private final Map<String, Room> rooms = new HashMap<>();

    private Room room(String eventId) {
        return rooms.computeIfAbsent(eventId, k -> new Room());
    }

    @Override
    public synchronized QueueStatus join(String eventId, String userId, Instant now) {
        Room r = room(eventId);
        Instant until = r.admitted.get(userId);
        if (until != null && now.isBefore(until)) {
            return QueueStatus.admitted(until);
        }
        r.admitted.remove(userId);
        r.queue.add(userId); // no-op if already queued: joining twice keeps the original place
        return status(eventId, userId, now);
    }

    @Override
    public synchronized QueueStatus status(String eventId, String userId, Instant now) {
        Room r = room(eventId);
        Instant until = r.admitted.get(userId);
        if (until != null && now.isBefore(until)) {
            return QueueStatus.admitted(until);
        }
        long ahead = 0;
        for (String u : r.queue) {
            if (u.equals(userId)) {
                return QueueStatus.queued(ahead);
            }
            ahead++;
        }
        return QueueStatus.notInQueue();
    }

    @Override
    public synchronized int admit(String eventId, int max, int maxActive, Duration accessTtl, Instant now) {
        Room r = room(eventId);
        r.admitted.values().removeIf(until -> !now.isBefore(until));
        int slots = Math.min(max, maxActive - r.admitted.size());
        int admitted = 0;
        Iterator<String> it = r.queue.iterator();
        while (admitted < slots && it.hasNext()) {
            r.admitted.put(it.next(), now.plus(accessTtl));
            it.remove();
            admitted++;
        }
        return admitted;
    }

    @Override
    public synchronized boolean isAdmitted(String eventId, String userId, Instant now) {
        Instant until = room(eventId).admitted.get(userId);
        return until != null && now.isBefore(until);
    }

    @Override
    public synchronized long queued(String eventId) {
        return room(eventId).queue.size();
    }

    @Override
    public synchronized long active(String eventId, Instant now) {
        return room(eventId).admitted.values().stream().filter(now::isBefore).count();
    }

    @Override
    public synchronized void leave(String eventId, String userId) {
        room(eventId).admitted.remove(userId);
    }
}
