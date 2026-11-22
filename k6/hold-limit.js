// 시나리오 B: 회원 1명이 서로 다른 좌석 10개를 동시에 선점하면 한도(기본 4)만큼만 성공해야 한다.
// 회원 행 잠금으로 매수를 세지 않으면 동시에 들어온 요청이 모두 한도 검사를 통과해 버린다.
// 실행: k6 run -e BASE=... -e SCHEDULE_ID=... -e SEAT_IDS=1,2,...,10 k6/hold-limit.js
// 주의: 대상 회원이 이 회차에 다른 선점이나 확정 예매가 없어야 한다 (demo-reset.sql 실행 후 load001은 1/17 회차가 비어 있다).
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  useExpectedStatuses, isSuccess, errorCode, requireEnv,
  loginAll, startTimeFromNow, waitUntil, holdSeats,
} from './lib/common.js';

useExpectedStatuses();

const SEAT_IDS = requireEnv('SEAT_IDS').split(',').map(Number);
const LIMIT = Number(__ENV.LIMIT || 4); // K-POP LIVE의 max_tickets_per_member
const MEMBER_EMAIL = __ENV.MEMBER_EMAIL || 'load001@example.com';
const holdSuccess = new Counter('hold_success');

export const options = {
  scenarios: {
    burst: { executor: 'per-vu-iterations', vus: SEAT_IDS.length, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    hold_success: [`count==${LIMIT}`],          // 한도만큼만 성공
    'http_req_failed{name:hold}': ['rate==0'],
    checks: ['rate==1'],
  },
};

export function setup() {
  return {
    scheduleId: requireEnv('SCHEDULE_ID'),
    memberId: loginAll([MEMBER_EMAIL])[0],
    startAt: startTimeFromNow(),
  };
}

export default function (data) {
  const seatId = SEAT_IDS[__VU - 1]; // VU마다 다른 좌석, 회원은 같음
  waitUntil(data.startAt);

  const res = holdSeats(data.memberId, data.scheduleId, [seatId]);

  holdSuccess.add(isSuccess(res) ? 1 : 0);
  check(res, {
    '성공 또는 409 HOLD_LIMIT_EXCEEDED': (r) =>
      isSuccess(r) || (r.status === 409 && errorCode(r) === 'HOLD_LIMIT_EXCEEDED'),
  });
}
