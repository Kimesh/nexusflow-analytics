import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 500 },   // Warm-up ramp
    { duration: '1m', target: 2000 },    // Sustained high load
    { duration: '30s', target: 5000 },   // Peak stress burst
    { duration: '30s', target: 0 },      // Graceful ramp-down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<30', 'p(99)<50'], // 99% under 50ms
    'http_req_failed': ['rate<0.001'],             // 99.99% success SLA
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

export default function () {
  const accounts = ['ACCT_01', 'ACCT_02', 'ACCT_03', 'ACCT_04', 'ACCT_05'];
  const sender = accounts[Math.floor(Math.random() * accounts.length)];
  let recipient = accounts[Math.floor(Math.random() * accounts.length)];
  while (recipient === sender) {
    recipient = accounts[Math.floor(Math.random() * accounts.length)];
  }

  const payload = JSON.stringify({
    senderAccountId: sender,
    recipientAccountId: recipient,
    amount: (Math.random() * 500 + 5).toFixed(2),
    currency: 'USD',
    transactionType: 'PAYMENT',
  });

  const idempotencyKey = `K6_${__VU}_${__ITER}_${Date.now()}`;

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/transactions`, payload, params);

  check(res, {
    'status is 202': (r) => r.status === 202,
    'has transaction id': (r) => JSON.parse(r.body).id !== undefined,
  });

  sleep(0.01); // 10ms pacing
}
