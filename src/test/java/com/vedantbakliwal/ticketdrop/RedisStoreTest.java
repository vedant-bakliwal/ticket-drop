package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.Inventory;
import com.vedantbakliwal.ticketdrop.core.WaitingRoom;
import com.vedantbakliwal.ticketdrop.redis.RedisInventory;
import com.vedantbakliwal.ticketdrop.redis.RedisWaitingRoom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Same contract tests against a real Redis in Docker. Skipped automatically when Docker is not running. */
@Testcontainers(disabledWithoutDocker = true)
class RedisStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @Nested
    class InventoryTest extends InventoryContract {
        final Inventory inventory = new RedisInventory(redis);

        @Override
        Inventory inventory() {
            return inventory;
        }
    }

    @Nested
    class WaitingRoomTest extends WaitingRoomContract {
        final WaitingRoom room = new RedisWaitingRoom(redis);

        @Override
        WaitingRoom room() {
            return room;
        }
    }
}
