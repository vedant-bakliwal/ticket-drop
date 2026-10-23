package com.vedantbakliwal.ticketdrop.core;

import java.time.Duration;

/**
 * One ticket drop.
 *
 * @param capacity            seats for sale
 * @param maxPerUser          seats one buyer may hold or buy in total
 * @param holdTtl             how long a seat hold lasts before the seats go back on sale
 * @param accessTtl           how long an admitted buyer may shop before re-queueing
 * @param admitPerTick        buyers let in from the queue per scheduler tick
 * @param maxActiveShoppers   cap on buyers shopping at the same time
 * @param priceCents          price per seat
 */
public record EventConfig(String eventId, int capacity, int maxPerUser, Duration holdTtl, Duration accessTtl,
                          int admitPerTick, int maxActiveShoppers, long priceCents) {

    public EventConfig {
        if (capacity <= 0 || maxPerUser <= 0 || admitPerTick <= 0 || maxActiveShoppers <= 0) {
            throw new IllegalArgumentException("capacity, limits and rates must be positive");
        }
    }
}
