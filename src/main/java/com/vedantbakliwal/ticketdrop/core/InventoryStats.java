package com.vedantbakliwal.ticketdrop.core;

/** Invariant: available + held + sold == capacity, and sold never exceeds capacity. */
public record InventoryStats(int capacity, int available, int held, int sold) {

    public boolean consistent() {
        return available >= 0 && held >= 0 && sold >= 0 && available + held + sold == capacity;
    }
}
