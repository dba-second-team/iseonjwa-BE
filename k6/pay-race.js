// 시나리오 E: 같은 회원이 결제 버튼을 여러 번 눌러 같은 좌석 결제 요청이 동시에 10건 들어온다
//   예매는 1건만 생기고 나머지는 409 HOLD_EXPIRED (이미 예매된 좌석이라 본인 선점이 아님)
// 실행: k6 run -e BASE=... -e SCHEDULE_ID=... -e PAY_SEAT_IDS=A,B k6/pay-race.js
// 예매가 생기므로 다시 돌리려면 demo-reset.sql부터 실행한다.
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  useExpectedStatuses, isSuccess, errorCode, requireEnv,
  loginLoadRange, startTimeFromNow, waitUntil, holdSeats, confirmReservation, mustSucceed,
} from './lib/common.js';

useExpectedStatuses();

const VUS = Number(__ENV.VUS || 10);
const paySuccess = new Counter('pay_success');

export const options = {
  setupTimeout: '1m',
  scenarios: {
    doubleClick: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    pay_success: ['count==1'],
    'http_req_failed{name:confirm}': ['rate==0'],
    'http_req_duration{name:confirm}': ['p(95)<3000'],
    checks: ['rate==1'],
  },
};

export function setup() {
  const scheduleId = requireEnv('SCHEDULE_ID');
  const seatIds = requireEnv('PAY_SEAT_IDS').split(',').map(Number);
  const [memberId] = loginLoadRange(101, 1); // load101
  mustSucceed(holdSeats(memberId, scheduleId, seatIds), '결제 전 좌석 선점');
  return { scheduleId, seatIds, memberId, startAt: startTimeFromNow() };
}

export default function (data) {
  waitUntil(data.startAt);
  const res = confirmReservation(data.memberId, data.scheduleId, data.seatIds);
  paySuccess.add(isSuccess(res) ? 1 : 0);
  check(res, {
    '결제 성공 또는 409 HOLD_EXPIRED': (r) =>
      isSuccess(r) || (r.status === 409 && errorCode(r) === 'HOLD_EXPIRED'),
  });
}
