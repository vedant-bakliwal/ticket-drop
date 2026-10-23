package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.Inventory;
import com.vedantbakliwal.ticketdrop.core.WaitingRoom;
import com.vedantbakliwal.ticketdrop.memory.InMemoryInventory;
import com.vedantbakliwal.ticketdrop.memory.InMemoryWaitingRoom;
import org.junit.jupiter.api.Nested;

class InMemoryStoreTest {

    @Nested
    class InventoryTest extends InventoryContract {
        final Inventory inventory = new InMemoryInventory();

        @Override
        Inventory inventory() {
            return inventory;
        }
    }

    @Nested
    class WaitingRoomTest extends WaitingRoomContract {
        final WaitingRoom room = new InMemoryWaitingRoom();

        @Override
        WaitingRoom room() {
            return room;
        }
    }
}
