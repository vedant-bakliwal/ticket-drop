// k6 load test: a crowd hits the drop at once. Each virtual buyer joins the queue, polls until
// admitted, holds seats and checks out (retrying with the same idempotency key on errors).
// At the end, the no-oversell invariant is checked on /stats.
//
//   k6 run load/drop.js                                   (app on localhost:8080)
//   k6 run -e BUYERS=2000 -e DECLINE=0.1 load/drop.js     (with 10% injected payment declines)
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const EVENT = __ENV.EVENT || "drop-2026";
const BUYERS = Number(__ENV.BUYERS || 1000);

const ordersPlaced = new Counter("orders_placed");
const soldOut = new Counter("sold_out");
const declined = new Counter("payments_declined");
const joinRetries = new Counter("join_retries");

export const options = {
  scenarios: {
    drop: { executor: "per-vu-iterations", vus: BUYERS, iterations: 1, maxDuration: "5m" },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:hold}": ["p(95)<500"],
    checks: ["rate>0.99"],
  },
};

export function setup() {
  const chaos = { declineRate: Number(__ENV.DECLINE || 0), latencyMs: Number(__ENV.PAY_LATENCY_MS || 50) };
  http.post(`${BASE}/admin/chaos`, JSON.stringify(chaos), { headers: { "Content-Type": "application/json" } });
}

// Join the waiting room, retrying (and honoring Retry-After) if the request fails.
function join(headers, url) {
  for (let i = 0; i < 5; i++) {
    const r = http.post(url("/queue"), null, {
      headers, tags: { name: "join" }, responseCallback: http.expectedStatuses(202, 429),
    });
    if (r.status === 202) return true;
    joinRetries.add(1);
    console.warn(`join failed for ${headers["X-User-Id"]}: HTTP ${r.status}`);
    sleep(Number(r.headers["Retry-After"] || 1));
  }
  return false;
}

export default function () {
  const user = `buyer-${__VU}`;
  const headers = { "X-User-Id": user, "Content-Type": "application/json" };
  const url = path => `${BASE}/events/${EVENT}${path}`;

  const joined = join(headers, url);
  check(joined, { "joined": ok => ok });
  if (!joined) return;

  let admitted = false;
  for (let i = 0; i < 240; i++) {               // wait up to 4 minutes for our turn
    const s = http.get(url("/queue"), { headers, tags: { name: "status" } });
    const state = s.status === 200 ? s.json("state") : null;
    if (state === "ADMITTED") { admitted = true; break; }
    if (state === "NOT_IN_QUEUE") join(headers, url);
    sleep(1);
  }
  check(admitted, { "admitted": ok => ok });
  if (!admitted) return;

  const qty = 1 + (__VU % 2);
  const hold = http.post(url("/holds"), JSON.stringify({ quantity: qty }), {
    headers, tags: { name: "hold" }, responseCallback: http.expectedStatuses(201, 409, 403),
  });
  check(hold, { "hold answered": r => [201, 409, 403].includes(r.status) });
  if (hold.status === 409) { soldOut.add(1); return; }
  if (hold.status !== 201) return;

  const key = `${user}-order-1`;
  for (let attempt = 0; attempt < 3; attempt++) {
    const o = http.post(url("/orders"), JSON.stringify({ holdId: hold.json("holdId") }), {
      headers: { ...headers, "Idempotency-Key": key }, tags: { name: "order" },
      responseCallback: http.expectedStatuses(200, 201, 402, 409, 410),
    });
    if (o.status === 201 || o.status === 200) { ordersPlaced.add(1); break; }
    if (o.status === 402) { declined.add(1); break; }
    sleep(0.5);                                   // 409 in progress: retry with the same key
  }
}

export function teardown() {
  const s = http.get(`${BASE}/events/${EVENT}/stats`).json();
  console.log(`stats: ${JSON.stringify(s)}`);
  const ok = check(s, {
    "never oversold": x => x.sold <= x.capacity,
    "seats add up (available + held + sold = capacity)": x => x.consistent === true,
  });
  if (!ok) throw new Error(`Inventory invariant broken: ${JSON.stringify(s)}`); // fails the run
}
