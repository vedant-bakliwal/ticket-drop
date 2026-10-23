package com.vedantbakliwal.ticketdrop.web;

import com.vedantbakliwal.ticketdrop.core.CheckoutResult;
import com.vedantbakliwal.ticketdrop.core.CheckoutService;
import com.vedantbakliwal.ticketdrop.core.DropService;
import com.vedantbakliwal.ticketdrop.core.EventConfig;
import com.vedantbakliwal.ticketdrop.core.HoldResult;
import com.vedantbakliwal.ticketdrop.core.Inventory;
import com.vedantbakliwal.ticketdrop.core.InventoryStats;
import com.vedantbakliwal.ticketdrop.core.OrderRepository;
import com.vedantbakliwal.ticketdrop.core.QueueStatus;
import com.vedantbakliwal.ticketdrop.core.WaitingRoom;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Buyer API. The buyer is identified by the X-User-Id header to keep the demo self-contained;
 * a real system would take it from an authenticated session.
 */
@RestController
@RequestMapping("/events/{eventId}")
public class DropController {

    private static final Pattern USER = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    public record HoldRequest(@Min(1) @Max(10) int quantity) { }

    public record CheckoutRequest(@NotBlank String holdId) { }

    private final EventConfig event;
    private final DropService drops;
    private final CheckoutService checkout;
    private final WaitingRoom room;
    private final Inventory inventory;
    private final OrderRepository orders;
    private final Clock clock;

    public DropController(EventConfig event, DropService drops, CheckoutService checkout, WaitingRoom room,
                          Inventory inventory, OrderRepository orders, Clock clock) {
        this.event = event;
        this.drops = drops;
        this.checkout = checkout;
        this.room = room;
        this.inventory = inventory;
        this.orders = orders;
        this.clock = clock;
    }

    /** Join the waiting room. Joining again keeps your place. */
    @PostMapping("/queue")
    public ResponseEntity<?> join(@PathVariable String eventId, @RequestHeader("X-User-Id") String userId) {
        checkEvent(eventId);
        checkUser(userId);
        DropService.JoinResult r = drops.join(event, userId, userId);
        if (r.outcome() == DropService.JoinOutcome.RATE_LIMITED) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "1")
                    .body(Map.of("error", "Too many requests. Wait a second and poll the status instead."));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view(r.status()));
    }

    @GetMapping("/queue")
    public Map<String, Object> status(@PathVariable String eventId, @RequestHeader("X-User-Id") String userId) {
        checkEvent(eventId);
        checkUser(userId);
        return view(drops.status(event, userId));
    }

    /** Hold seats. Only buyers who have been admitted from the queue can hold. */
    @PostMapping("/holds")
    public ResponseEntity<?> hold(@PathVariable String eventId, @RequestHeader("X-User-Id") String userId,
                                  @Valid @RequestBody HoldRequest body) {
        checkEvent(eventId);
        checkUser(userId);
        DropService.HoldAttempt attempt = drops.hold(event, userId, body.quantity());
        return switch (attempt.outcome()) {
            case NOT_ADMITTED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Not admitted yet. Join the queue and wait for your turn."));
            case INVALID_QUANTITY -> ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", "Quantity must be between 1 and " + event.maxPerUser()));
            case OK -> {
                if (attempt.result().outcome() == HoldResult.Outcome.SOLD_OUT) {
                    room.leave(event.eventId(), userId); // nothing left to buy: free the slot
                }
                yield holdResponse(attempt.result());
            }
        };
    }

    private ResponseEntity<?> holdResponse(HoldResult r) {
        return switch (r.outcome()) {
            case HELD -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("holdId", r.holdId(), "quantity", r.quantity(), "expiresAt", r.expiresAt().toString()));
            case SOLD_OUT -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Sold out"));
            case OVER_LIMIT -> ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", "Limit is " + event.maxPerUser() + " seats per buyer"));
        };
    }

    /** Buy held seats. Send the same Idempotency-Key when retrying: you are charged at most once. */
    @PostMapping("/orders")
    public ResponseEntity<?> order(@PathVariable String eventId, @RequestHeader("X-User-Id") String userId,
                                   @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CheckoutRequest body) {
        checkEvent(eventId);
        checkUser(userId);
        if (!KEY.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 8-100 characters: letters, digits, . _ : -");
        }
        CheckoutResult r = checkout.checkout(event, userId, body.holdId(), key);
        HttpStatus status = switch (r.outcome()) {
            case CREATED -> HttpStatus.CREATED;
            case REPLAYED -> HttpStatus.OK;
            case IN_PROGRESS -> HttpStatus.CONFLICT;
            case NOT_YOUR_HOLD -> HttpStatus.NOT_FOUND;
            case HOLD_EXPIRED -> HttpStatus.GONE;
            case PAYMENT_DECLINED -> HttpStatus.PAYMENT_REQUIRED;
        };
        if (status == HttpStatus.CREATED || status == HttpStatus.OK) {
            room.leave(event.eventId(), userId);   // done shopping: let the next buyer in
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("result", r.outcome().name());
        out.put("message", r.message());
        if (r.order() != null) {
            out.put("orderId", r.order().orderId());
            out.put("status", r.order().status().name());
            out.put("quantity", r.order().quantity());
            out.put("amountCents", r.order().amountCents());
        }
        return ResponseEntity.status(status).body(out);
    }

    /** Live numbers for the drop. The load test checks the no-oversell invariant here. */
    @GetMapping("/stats")
    public Map<String, Object> stats(@PathVariable String eventId) {
        checkEvent(eventId);
        InventoryStats s = inventory.stats(event.eventId());
        return Map.of("capacity", s.capacity(), "available", s.available(), "held", s.held(), "sold", s.sold(),
                "consistent", s.consistent(), "queued", room.queued(event.eventId()),
                "activeShoppers", room.active(event.eventId(), clock.instant()),
                "ordersCompleted", orders.countCompleted(event.eventId()));
    }

    private void checkEvent(String eventId) {
        if (!event.eventId().equals(eventId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown event " + eventId);
        }
    }

    private static void checkUser(String userId) {
        if (!USER.matcher(userId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id must be 1-64 characters: letters, digits, . _ -");
        }
    }

    private static Map<String, Object> view(QueueStatus s) {
        return switch (s.state()) {
            case QUEUED -> Map.of("state", "QUEUED", "ahead", s.ahead());
            case ADMITTED -> Map.of("state", "ADMITTED", "accessExpiresAt", s.accessExpiresAt().toString());
            case NOT_IN_QUEUE -> Map.of("state", "NOT_IN_QUEUE");
        };
    }
}
