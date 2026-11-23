// 시나리오 F: 같은 예매에 취소 요청이 동시에 10건 들어온다
//   취소는 1건만 성공하고 나머지는 409 ALREADY_CANCELED. 좌석은 한 번만 빈 좌석으로 돌아간다.
// 실행: k6 run -e BASE=... -e SCHEDULE_ID=... -e CANCEL_SEAT_IDS=A,B k6/cancel-race.js
// 예매가 생기므로 다시 돌리려면 demo-reset.sql부터 실행한다.
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  useExpectedStatuses, isSuccess, errorCode, requireEnv,
  loginLoadRange, startTimeFromNow, waitUntil, holdSeats, confirmReservation, cancelReservation, mustSucceed,
} from './lib/common.js';

useExpectedStatuses();

const VUS = Number(__ENV.VUS || 10);
const cancelSuccess = new Counter('cancel_success');

export const options = {
  setupTimeout: '1m',
  scenarios: {
    doubleCancel: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    cancel_success: ['count==1'],
    'http_req_failed{name:cancel}': ['rate==0'],
    'http_req_duration{name:cancel}': ['p(95)<3000'],
    checks: ['rate==1'],
  },
};

export function setup() {
  const scheduleId = requireEnv('SCHEDULE_ID');
  const seatIds = requireEnv('CANCEL_SEAT_IDS').split(',').map(Number);
  const [memberId] = loginLoadRange(102, 1); // load102
  mustSucceed(holdSeats(memberId, scheduleId, seatIds), '취소 전 좌석 선점');
  const pay = mustSucceed(confirmReservation(memberId, scheduleId, seatIds), '취소 전 결제');
  return { memberId, reservationId: pay.json('data.reservationId'), startAt: startTimeFromNow() };
}

export default function (data) {
  waitUntil(data.startAt);
  const res = cancelReservation(data.memberId, data.reservationId);
  cancelSuccess.add(isSuccess(res) ? 1 : 0);
  check(res, {
    '취소 성공 또는 409 ALREADY_CANCELED': (r) =>
      isSuccess(r) || (r.status === 409 && errorCode(r) === 'ALREADY_CANCELED'),
  });
}
