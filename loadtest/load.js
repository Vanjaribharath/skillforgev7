// k6 load test — exercises the real login + question catalog browsing flow
// under increasing concurrent load. Requires demo credentials to exist
// (call POST /api/v1/skillforge/demo/bootstrap once first) or pass your own
// via TEST_EMAIL/TEST_PASSWORD/ORG_ID.
//
// Usage:
//   k6 run \
//     -e BASE_URL=https://your-app.up.railway.app \
//     -e ORG_ID=<your organization id> \
//     loadtest/load.js

import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ORG_ID = __ENV.ORG_ID || "";
const EMAIL = __ENV.TEST_EMAIL || "admin@apex.example";
const PASSWORD = __ENV.TEST_PASSWORD || "Demo@12345";

export const options = {
  stages: [
    { duration: "30s", target: 10 },  // ramp up
    { duration: "2m", target: 50 },   // sustained load
    { duration: "30s", target: 0 },   // ramp down
  ],
  thresholds: {
    http_req_duration: ["p(95)<800", "p(99)<1500"],
    http_req_failed: ["rate<0.02"],
  },
};

export default function () {
  const loginRes = http.post(
    `${BASE_URL}/api/v1/skillforge/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
  );
  const loggedIn = check(loginRes, {
    "login succeeded": (r) => r.status === 200,
    "received access token": (r) => !!r.json("accessToken"),
  });
  if (!loggedIn) {
    sleep(1);
    return;
  }

  const token = loginRes.json("accessToken");
  const authHeaders = { headers: { Authorization: `Bearer ${token}` } };
  const orgId = ORG_ID || loginRes.json("user.organizationId");

  const coverage = http.get(`${BASE_URL}/api/v1/skillforge/catalog/coverage?organizationId=${orgId}`);
  check(coverage, { "coverage loaded": (r) => r.status === 200 });

  const questions = http.get(
    `${BASE_URL}/api/v1/skillforge/catalog/questions?organizationId=${orgId}&subject=java&difficulty=EASY&size=25`,
  );
  check(questions, { "questions loaded": (r) => r.status === 200 });

  const health = http.get(`${BASE_URL}/actuator/health`);
  check(health, { "health still UP": (r) => r.status === 200 });

  sleep(Math.random() * 2 + 1);
}
