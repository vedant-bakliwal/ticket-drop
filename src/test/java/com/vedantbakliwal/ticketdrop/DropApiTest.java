package com.vedantbakliwal.ticketdrop;

import com.vedantbakliwal.ticketdrop.core.DropService;
import com.vedantbakliwal.ticketdrop.core.EventConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole buyer flow over HTTP, in the default in-memory mode (no Docker needed). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ticketdrop.tick-ms=3600000", "ticketdrop.event.capacity=5"})
class DropApiTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    DropService drops;

    @Autowired
    EventConfig event;

    @SuppressWarnings({"rawtypes", "unchecked"})
    ResponseEntity<Map> call(HttpMethod method, String path, String user, Object body, String idempotencyKey) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Id", user);
        if (idempotencyKey != null) h.set("Idempotency-Key", idempotencyKey);
        return http.exchange("/events/drop-2026" + path, method, new HttpEntity<>(body, h), Map.class);
    }

    @Test
    void joinWaitHoldBuyAndRetrySafely() {
        assertThat(call(HttpMethod.POST, "/queue", "ada", null, null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(call(HttpMethod.POST, "/holds", "ada", Map.of("quantity", 2), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        drops.tick(event); // what the scheduler does every second
        assertThat(call(HttpMethod.GET, "/queue", "ada", null, null).getBody()).containsEntry("state", "ADMITTED");

        ResponseEntity<Map> hold = call(HttpMethod.POST, "/holds", "ada", Map.of("quantity", 2), null);
        assertThat(hold.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String holdId = (String) hold.getBody().get("holdId");

        ResponseEntity<Map> order = call(HttpMethod.POST, "/orders", "ada", Map.of("holdId", holdId), "ada-order-1");
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(order.getBody()).containsEntry("amountCents", 15000);

        ResponseEntity<Map> retry = call(HttpMethod.POST, "/orders", "ada", Map.of("holdId", holdId), "ada-order-1");
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody().get("orderId")).isEqualTo(order.getBody().get("orderId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) call(HttpMethod.GET, "/stats", "ada", null, null).getBody();
        assertThat(stats).containsEntry("sold", 2).containsEntry("consistent", true).containsEntry("ordersCompleted", 1);
    }

    @Test
    void joinIsRateLimitedPerBuyer() {
        int limited = 0;
        for (int i = 0; i < 10; i++) {
            if (call(HttpMethod.POST, "/queue", "spammer", null, null).getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) limited++;
        }
        assertThat(limited).isBetween(4, 5); // burst of 5; a slow machine may refill one token mid-loop
    }

    @Test
    void rejectsBadInput() {
        assertThat(call(HttpMethod.POST, "/queue", "bad user!", null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/events/other/stats", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
