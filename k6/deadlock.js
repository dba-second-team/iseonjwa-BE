// 시나리오 C: 두 좌석을 절반은 [A, B], 절반은 [B, A] 순서로 동시에 요청한다.
// 좌석을 ID 순으로 잠그면 데드락 없이 1명만 성공한다.
// 요청 순서대로 잠그면 InnoDB가 데드락을 감지해 한쪽을 롤백하고, 그 요청은 500으로 끝난다.
// 실행: k6 run -e BASE=... -e SCHEDULE_ID=... -e SEAT_PAIR=A,B k6/deadlock.js
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  useExpectedStatuses, isSuccess, errorCode, requireEnv,
  loginLoadMembers, startTimeFromNow, waitUntil, holdSeats,
} from './lib/common.js';

useExpectedStatuses();

const VUS = Number(__ENV.VUS || 20);
const PAIR = requireEnv('SEAT_PAIR').split(',').map(Number);
const holdSuccess = new Counter('hold_success');

export const options = {
  setupTimeout: '1m',
  scenarios: {
    crossed: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    hold_success: ['count==1'],
    'http_req_failed{name:hold}': ['rate==0'],          // 데드락이면 여기서 걸린다
    'http_req_duration{name:hold}': ['max<3000'],       // 락을 오래 기다리지 않는지
    checks: ['rate==1'],
  },
};

export function setup() {
  if (PAIR.length !== 2) throw new Error('SEAT_PAIR에는 좌석 ID 두 개를 넣어 주세요. 예: -e SEAT_PAIR=892,864');
  return {
    scheduleId: requireEnv('SCHEDULE_ID'),
    memberIds: loginLoadMembers(VUS),
    startAt: startTimeFromNow(),
  };
}

export default function (data) {
  const memberId = data.memberIds[__VU - 1];
  const seatIds = __VU % 2 === 0 ? [PAIR[0], PAIR[1]] : [PAIR[1], PAIR[0]];
  waitUntil(data.startAt);

  const res = holdSeats(memberId, data.scheduleId, seatIds);

  holdSuccess.add(isSuccess(res) ? 1 : 0);
  check(res, {
    '성공 또는 409 SEAT_UNAVAILABLE': (r) =>
      isSuccess(r) || (r.status === 409 && errorCode(r) === 'SEAT_UNAVAILABLE'),
  });
}
