package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.DropService;
import com.vedantbakliwal.ticketdrop.core.EventConfig;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every tick: return expired holds to sale, then let the next buyers in from the queue. With
 * the Redis store several instances may tick at once; the admit script keeps the active-shopper
 * cap exact anyway.
 */
@Component
public class DropScheduler {

    private final DropService drops;
    private final EventConfig event;

    public DropScheduler(DropService drops, EventConfig event) {
        this.drops = drops;
        this.event = event;
    }

    @Scheduled(fixedRateString = "${ticketdrop.tick-ms:1000}")
    public void tick() {
        drops.tick(event);
    }
}
