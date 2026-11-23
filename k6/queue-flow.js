// 시나리오 D: 대기열부터 결제까지 실제 예매 순서대로 동시에 몰리는 상황
//   1단계 (0초)  회원 100명이 같은 공연 대기열에 동시에 등록한다
//   2단계 (3초)  100명이 동시에 입장을 시도한다 → 정원(4명)만 입장, 입장한 사람은 대기 순번 1~4번
//   3단계 (6초)  입장한 4명이 같은 좌석 1개를 동시에 선점한다 → 1명만 성공
//   4단계 (6초)  선점에 성공한 1명이 바로 결제한다 → 결제가 끝나면 작업열에서 빠진다
//   5단계 (10초) 남은 96명이 다시 입장을 시도한다 → 빈자리 1개만큼 1명만 입장
// 실행: k6 run -e BASE=... -e PERFORMANCE_ID=... -e SCHEDULE_ID=... -e FLOW_SEAT_ID=... k6/queue-flow.js
// 다른 사람이 같은 공연 대기열을 쓰지 않을 때 실행한다. 끝나면 100명을 모두 대기열에서 뺀다.
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  useExpectedStatuses, isSuccess, errorCode, requireEnv,
  loginLoadRange, startTimeFromNow, waitUntil, holdSeats,
  registerWaiting, enterActive, leaveQueueAll, confirmReservation,
} from './lib/common.js';

useExpectedStatuses();

const VUS = Number(__ENV.VUS || 100);
const MAX_ACTIVE = Number(__ENV.MAX_ACTIVE || 4); // 서버 queue.active.max-size
const FIRST_MEMBER = 201;                          // load201 ~ load300

const waitingSuccess = new Counter('waiting_success');
const firstAdmitted = new Counter('first_admitted');
const outOfTurn = new Counter('admitted_out_of_turn');
const holdSuccess = new Counter('hold_success');
const paySuccess = new Counter('pay_success');
const lateAdmitted = new Counter('late_admitted');

export const options = {
  setupTimeout: '2m',
  scenarios: {
    flow: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    waiting_success: [`count==${VUS}`],
    first_admitted: [`count==${MAX_ACTIVE}`],
    admitted_out_of_turn: ['count==0'],
    hold_success: ['count==1'],
    pay_success: ['count==1'],
    late_admitted: ['count==1'],
    'http_req_failed{name:waiting}': ['rate==0'],
    'http_req_failed{name:active}': ['rate==0'],
    'http_req_failed{name:hold}': ['rate==0'],
    'http_req_failed{name:confirm}': ['rate==0'],
    'http_req_duration{name:waiting}': ['p(95)<3000'],
    'http_req_duration{name:hold}': ['p(95)<3000'],
    checks: ['rate==1'],
  },
};

export function setup() {
  const memberIds = loginLoadRange(FIRST_MEMBER, VUS);
  leaveQueueAll(memberIds); // 이전 실행에서 남은 대기열 정리
  const t0 = startTimeFromNow();
  return {
    performanceId: Number(requireEnv('PERFORMANCE_ID')),
    scheduleId: requireEnv('SCHEDULE_ID'),
    seatId: Number(requireEnv('FLOW_SEAT_ID')),
    memberIds,
    t0,
    tEnter: t0 + 3000,
    tHold: t0 + 6000,
    tLate: t0 + 10000,
  };
}

export default function (data) {
  const memberId = data.memberIds[__VU - 1];

  // 1단계: 대기열 등록
  waitUntil(data.t0);
  const waiting = registerWaiting(memberId, data.performanceId);
  const position = isSuccess(waiting) ? waiting.json('data.position') : null;
  waitingSuccess.add(isSuccess(waiting) ? 1 : 0);
  check(waiting, { '대기열 등록 성공': (r) => isSuccess(r) && position >= 1 && position <= VUS });

  // 2단계: 동시 입장 시도
  waitUntil(data.tEnter);
  const first = enterActive(memberId, data.performanceId);
  const admitted = isSuccess(first);
  firstAdmitted.add(admitted ? 1 : 0);
  outOfTurn.add(admitted && position > MAX_ACTIVE ? 1 : 0);
  check(first, {
    '입장 성공 또는 409 CANNOT_ENTER_ACTIVE_QUEUE': (r) =>
      isSuccess(r) || (r.status === 409 && errorCode(r) === 'CANNOT_ENTER_ACTIVE_QUEUE'),
  });

  if (admitted) {
    // 3단계: 입장한 사람끼리 같은 좌석 선점
    waitUntil(data.tHold);
    const hold = holdSeats(memberId, data.scheduleId, [data.seatId]);
    holdSuccess.add(isSuccess(hold) ? 1 : 0);
    check(hold, {
      '선점 성공 또는 409 SEAT_UNAVAILABLE': (r) =>
        isSuccess(r) || (r.status === 409 && errorCode(r) === 'SEAT_UNAVAILABLE'),
    });

    // 4단계: 선점한 사람만 결제 → 작업열에서 빠진다
    if (isSuccess(hold)) {
      const pay = confirmReservation(memberId, data.scheduleId, [data.seatId]);
      paySuccess.add(isSuccess(pay) ? 1 : 0);
      check(pay, { '결제 성공': (r) => isSuccess(r) });
    }
    return;
  }

  // 5단계: 결제로 생긴 빈자리에 대기자가 들어가는지
  waitUntil(data.tLate);
  const late = enterActive(memberId, data.performanceId, 'active');
  lateAdmitted.add(isSuccess(late) ? 1 : 0);
  check(late, {
    '재입장 성공 또는 409 CANNOT_ENTER_ACTIVE_QUEUE': (r) =>
      isSuccess(r) || (r.status === 409 && errorCode(r) === 'CANNOT_ENTER_ACTIVE_QUEUE'),
  });
}

export function teardown(data) {
  leaveQueueAll(data.memberIds);
}
