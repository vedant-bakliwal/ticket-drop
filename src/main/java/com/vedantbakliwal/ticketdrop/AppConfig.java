package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.CheckoutService;
import com.vedantbakliwal.ticketdrop.core.DropService;
import com.vedantbakliwal.ticketdrop.core.EventConfig;
import com.vedantbakliwal.ticketdrop.core.Inventory;
import com.vedantbakliwal.ticketdrop.core.OrderRepository;
import com.vedantbakliwal.ticketdrop.core.TokenBucket;
import com.vedantbakliwal.ticketdrop.core.WaitingRoom;
import com.vedantbakliwal.ticketdrop.memory.FakePaymentGateway;
import com.vedantbakliwal.ticketdrop.memory.InMemoryInventory;
import com.vedantbakliwal.ticketdrop.memory.InMemoryWaitingRoom;
import com.vedantbakliwal.ticketdrop.redis.RedisInventory;
import com.vedantbakliwal.ticketdrop.redis.RedisWaitingRoom;
import com.vedantbakliwal.ticketdrop.store.JdbcOrderRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EventConfig eventConfig(DropProperties props) {
        return props.eventConfig();
    }

    @Bean
    TokenBucket joinLimiter(DropProperties props) {
        return new TokenBucket(props.limits().joinBurst(), props.limits().joinPerSecond());
    }

    @Bean
    FakePaymentGateway paymentGateway(DropProperties props) {
        return new FakePaymentGateway(props.chaos().declineRate(), props.chaos().latencyMs());
    }

    @Bean
    OrderRepository orderRepository(JdbcClient jdbc) {
        return new JdbcOrderRepository(jdbc);
    }

    @Bean
    DropService dropService(WaitingRoom room, Inventory inventory, TokenBucket joinLimiter, Clock clock, EventConfig event) {
        inventory.initialize(event.eventId(), event.capacity());
        return new DropService(room, inventory, joinLimiter, clock);
    }

    @Bean
    CheckoutService checkoutService(Inventory inventory, OrderRepository orders, FakePaymentGateway payments, Clock clock) {
        return new CheckoutService(inventory, orders, payments, clock);
    }

    /** Default: everything in this JVM. Runs anywhere; one instance only. */
    @Configuration
    @ConditionalOnProperty(name = "ticketdrop.store", havingValue = "memory", matchIfMissing = true)
    static class MemoryStore {
        @Bean
        WaitingRoom waitingRoom() {
            return new InMemoryWaitingRoom();
        }

        @Bean
        Inventory inventory() {
            return new InMemoryInventory();
        }
    }

    /** Shared state in Redis: any number of app instances can serve the same drop. */
    @Configuration
    @ConditionalOnProperty(name = "ticketdrop.store", havingValue = "redis")
    static class RedisStore {
        @Bean
        WaitingRoom waitingRoom(StringRedisTemplate redis) {
            return new RedisWaitingRoom(redis);
        }

        @Bean
        Inventory inventory(StringRedisTemplate redis) {
            return new RedisInventory(redis);
        }
    }
}
