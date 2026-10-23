CREATE TABLE IF NOT EXISTS orders (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    order_id        VARCHAR(40),
    event_id        VARCHAR(60)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    hold_id         VARCHAR(60)  NOT NULL,
    quantity        INT          NOT NULL,
    amount_cents    BIGINT       NOT NULL,
    status          VARCHAR(12)  NOT NULL,
    failure_reason  VARCHAR(200),
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS orders_event_status ON orders (event_id, status);
