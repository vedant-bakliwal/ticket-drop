package com.vedantbakliwal.ticketdrop.web;

import com.vedantbakliwal.ticketdrop.memory.FakePaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Failure injection for load tests. Only exists when ticketdrop.chaos.enabled=true. */
@RestController
@RequestMapping("/admin/chaos")
@ConditionalOnProperty(name = "ticketdrop.chaos.enabled", havingValue = "true")
public class ChaosController {

    public record Settings(double declineRate, long latencyMs) { }

    private final FakePaymentGateway payments;

    public ChaosController(FakePaymentGateway payments) {
        this.payments = payments;
    }

    @PostMapping
    public Map<String, Object> set(@RequestBody Settings s) {
        payments.configure(s.declineRate(), s.latencyMs());
        return get();
    }

    @GetMapping
    public Map<String, Object> get() {
        return Map.of("declineRate", payments.declineRate(), "latencyMs", payments.latencyMs(),
                "charges", payments.charges(), "refunds", payments.refunds());
    }
}
