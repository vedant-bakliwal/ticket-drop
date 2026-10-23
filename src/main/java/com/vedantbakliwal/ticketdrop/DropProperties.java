package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.EventConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Settings under {@code ticketdrop.*} in application.yml. */
@ConfigurationProperties("ticketdrop")
public record DropProperties(String store, Event event, Limits limits, Chaos chaos) {

    public record Event(String id, int capacity, int maxPerUser, Duration holdTtl, Duration accessTtl,
                        int admitPerTick, int maxActiveShoppers, long priceCents) {
    }

    /** Join endpoint limiter: burst size and refill per second, per buyer. */
    public record Limits(double joinBurst, double joinPerSecond) {
    }

    /** Failure injection for the fake payment provider. */
    public record Chaos(boolean enabled, double declineRate, long latencyMs) {
    }

    public EventConfig eventConfig() {
        return new EventConfig(event.id(), event.capacity(), event.maxPerUser(), event.holdTtl(), event.accessTtl(),
                event.admitPerTick(), event.maxActiveShoppers(), event.priceCents());
    }
}
