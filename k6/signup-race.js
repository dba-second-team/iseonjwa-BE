// 같은 이메일로 50명이 동시에 회원가입하면 1명만 성공해야 한다.
// 실행: k6 run -e BASE=http://localhost:8080 k6/signup-race.js
// 만들어진 회원(k6-signup-race@example.com)은 scripts/demo-reset.sql이 지운다.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE, useExpectedStatuses, errorCode, startTimeFromNow, waitUntil } from './lib/common.js';

useExpectedStatuses();

const VUS = Number(__ENV.VUS || 50);
const EMAIL = __ENV.EMAIL || 'k6-signup-race@example.com';
const created = new Counter('signup_created');

export const options = {
  scenarios: {
    race: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    signup_created: ['count==1'],             // 성공은 딱 1건
    'http_req_failed{name:signup}': ['rate==0'], // 201, 409 말고 다른 응답은 0건
    checks: ['rate==1'],
  },
};

export function setup() {
  return { startAt: startTimeFromNow() };
}

export default function (data) {
  waitUntil(data.startAt);

  const res = http.post(
    `${BASE}/api/members`,
    JSON.stringify({ email: EMAIL, password: 'Demo1234!', name: 'k6경쟁', phone: '01012345678' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'signup' } },
  );

  created.add(res.status === 201 ? 1 : 0); // 0도 기록해야 성공이 0건일 때 threshold가 실패한다
  check(res, {
    '201 또는 409 EMAIL_DUPLICATED': (r) =>
      r.status === 201 || (r.status === 409 && errorCode(r) === 'EMAIL_DUPLICATED'),
  });
}
