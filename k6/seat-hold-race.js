// 시나리오 A: 회원 100명이 같은 좌석 1개를 동시에 선점하면 1명만 성공해야 한다.
// 실행: k6 run -e BASE=... -e SCHEDULE_ID=... -e SEAT_ID=... k6/seat-hold-race.js
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  useExpectedStatuses, isSuccess, errorCode, requireEnv,
  loginLoadMembers, startTimeFromNow, waitUntil, holdSeats,
} from './lib/common.js';

useExpectedStatuses();

const VUS = Number(__ENV.VUS || 100);
const holdSuccess = new Counter('hold_success');

export const options = {
  setupTimeout: '2m',
  scenarios: {
    race: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    hold_success: ['count==1'],                 // 성공은 딱 1명
    'http_req_failed{name:hold}': ['rate==0'],  // 500(락 대기 초과, 데드락 등)은 0건
    checks: ['rate==1'],
  },
};

export function setup() {
  return {
    scheduleId: requireEnv('SCHEDULE_ID'),
    seatId: Number(requireEnv('SEAT_ID')),
    memberIds: loginLoadMembers(VUS), // load001 ~ load100
    startAt: startTimeFromNow(),
  };
}

export default function (data) {
  const memberId = data.memberIds[__VU - 1]; // VU마다 다른 회원
  waitUntil(data.startAt);

  const res = holdSeats(memberId, data.scheduleId, [data.seatId]);

  holdSuccess.add(isSuccess(res) ? 1 : 0);
  check(res, {
    '성공 또는 409 SEAT_UNAVAILABLE': (r) =>
      isSuccess(r) || (r.status === 409 && errorCode(r) === 'SEAT_UNAVAILABLE'),
  });
}
