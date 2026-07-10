// k6 smoke test — confirms the app is up and responding correctly under
// light, steady load. Run this first, before the heavier load/stress tests.
//
// Usage:
//   k6 run -e BASE_URL=https://your-app.up.railway.app loadtest/smoke.js
//
// Install k6: https://k6.io/docs/get-started/installation/

import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
  vus: 5,
  duration: "30s",
  thresholds: {
    http_req_duration: ["p(95)<500"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const health = http.get(`${BASE_URL}/actuator/health`);
  check(health, {
    "health status is 200": (r) => r.status === 200,
    "health reports UP": (r) => r.json("status") === "UP",
  });
  sleep(1);
}
