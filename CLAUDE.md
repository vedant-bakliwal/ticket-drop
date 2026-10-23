# ticket-drop

Java 21 / Spring Boot 3.5. Sells a fixed number of seats to a crowd without overselling:
FIFO waiting room, time-boxed seat holds, idempotent checkout, per-buyer rate limiting.

- Build and test: `mvn -B verify` (Redis contract tests run only when Docker is available).
- Run alone (in-memory, H2): `mvn spring-boot:run`
- Run the distributed setup: `docker compose up --build` (2 instances + Redis + PostgreSQL + nginx on :8080)
- Load test: `k6 run load/drop.js`

Rules for changes:
- `core/` is plain Java with no Spring imports; keep it that way so it stays fast to test.
- Every Inventory/WaitingRoom change must pass both contract test suites (memory and Redis).
- Any Redis state change that reads then writes goes in a Lua script, never two separate calls.
- Invariant: available + held + sold == capacity at all times. The load test enforces it.
