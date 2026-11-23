import http from 'k6/http';
import { sleep } from 'k6';

export const BASE = (__ENV.BASE || 'http://localhost:8080').replace(/\/+$/, '');
export const PASSWORD = __ENV.PASSWORD || 'Demo1234!';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 200, 201은 성공이고 409는 "정상적으로 거절된" 응답이다.
// 이 셋을 뺀 응답(400, 500 등)만 http_req_failed에 실패로 잡힌다.
export function useExpectedStatuses() {
  http.setResponseCallback(http.expectedStatuses(200, 201, 409));
}

export const isSuccess = (res) => res.status === 200 || res.status === 201;

export function errorCode(res) {
  try {
    const body = res.json();
    return body.errorCode ?? body.code ?? null;
  } catch (e) {
    return null;
  }
}

export function requireEnv(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`-e ${name}=값 을 넣어 주세요. ID는 k6/sql/find-targets.sql로 찾을 수 있어요.`);
  }
  return value;
}

export const loadEmail = (n) => `load${String(n).padStart(3, '0')}@example.com`;

function loginRequest(email) {
  return [
    'POST',
    `${BASE}/api/members/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: 'login' } },
  ];
}

// 로그인해서 memberId를 받아 온다. X-Member-Id 헤더에 넣을 값이다.
export function loginAll(emails) {
  const responses = http.batch(emails.map(loginRequest));
  return responses.map((res, i) => {
    if (res.status !== 200) {
      throw new Error(`로그인 실패: ${emails[i]} (${res.status}) ${res.body}`);
    }
    return res.json('data.memberId');
  });
}

// load001부터 count명
export function loginLoadMembers(count) {
  const emails = [];
  for (let i = 1; i <= count; i++) emails.push(loadEmail(i));
  return loginAll(emails);
}

// 모든 VU가 같은 순간에 요청을 보내도록 setup에서 정한 시각까지 기다린다
export function startTimeFromNow(ms = 2000) {
  return Date.now() + ms;
}

export function waitUntil(startAt) {
  const remaining = startAt - Date.now();
  if (remaining > 0) sleep(remaining / 1000);
}

// ============================================
// 좌석 선점 API 호출 (#23 SeatController 기준)
//   POST /api/schedules/{scheduleId}/seats/hold
//   헤더 X-Member-Id, 본문 {"seatIds": [1, 2]}
//   성공 200 {"data": {"seatIds": [...], "heldUntil": "..."}}
//   실패 409 {"errorCode": "SEAT_UNAVAILABLE" | "HOLD_LIMIT_EXCEEDED", "message": "..."}
// 매수 한도는 회차 단위로 센다 (만료 전 선점 + 확정 예매 좌석)
// API 명세가 바뀌면 이 함수만 고치면 된다.
// ============================================
export function holdSeats(memberId, scheduleId, seatIds) {
  return http.post(
    `${BASE}/api/schedules/${scheduleId}/seats/hold`,
    JSON.stringify({ seatIds }),
    {
      headers: Object.assign({ 'X-Member-Id': String(memberId) }, JSON_HEADERS),
      tags: { name: 'hold' },
    },
  );
}

export function memberHeaders(memberId) {
  return Object.assign({ 'X-Member-Id': String(memberId) }, JSON_HEADERS);
}

// 테스트용 회원을 번호 범위로 로그인한다. 예: loginLoadRange(201, 100) → load201 ~ load300
export function loginLoadRange(from, count) {
  const emails = [];
  for (let i = from; i < from + count; i++) emails.push(loadEmail(i));
  return loginAll(emails);
}

// ============================================
// 대기열 API (#QueueController 기준)
//   POST /api/queue/waiting   {"performanceId"} → 201 {"data": {"position": n}}
//   POST /api/queue/active    {"performanceId"} → 200, 정원이 찼거나 순번이 아니면 409 CANNOT_ENTER_ACTIVE_QUEUE
//   DELETE /api/queue          → 200, 대기열에 없으면 404
// ============================================
export function registerWaiting(memberId, performanceId) {
  return http.post(`${BASE}/api/queue/waiting`, JSON.stringify({ performanceId }),
    { headers: memberHeaders(memberId), tags: { name: 'waiting' } });
}

export function enterActive(memberId, performanceId, tagName = 'active') {
  return http.post(`${BASE}/api/queue/active`, JSON.stringify({ performanceId }),
    { headers: memberHeaders(memberId), tags: { name: tagName } });
}

// 여러 회원을 한 번에 대기열에서 뺀다 (이전 실행의 흔적 정리용)
export function leaveQueueAll(memberIds) {
  http.batch(memberIds.map((id) => [
    'DELETE', `${BASE}/api/queue`, null,
    { headers: memberHeaders(id), tags: { name: 'leave' }, responseCallback: http.expectedStatuses(200, 404) },
  ]));
}

// ============================================
// 예매 API (#ReservationController 기준)
//   POST /api/reservations  {"scheduleId", "seatIds", "paymentMethod"} → 201 {"data": {"reservationId"}}
//     본인 선점이 아니거나 만료면 409 HOLD_EXPIRED
//   POST /api/reservations/{id}/cancel → 200, 이미 취소면 409 ALREADY_CANCELED
// ============================================
export function confirmReservation(memberId, scheduleId, seatIds, paymentMethod = 'CARD') {
  return http.post(`${BASE}/api/reservations`,
    JSON.stringify({ scheduleId: Number(scheduleId), seatIds, paymentMethod }),
    { headers: memberHeaders(memberId), tags: { name: 'confirm' } });
}

export function cancelReservation(memberId, reservationId) {
  return http.post(`${BASE}/api/reservations/${reservationId}/cancel`, null,
    { headers: memberHeaders(memberId), tags: { name: 'cancel' } });
}

// setup에서 쓰는 준비 단계: 실패하면 테스트 자체를 멈춘다
export function mustSucceed(res, what) {
  if (!isSuccess(res)) {
    throw new Error(`${what} 실패 (${res.status}) ${res.body}. demo-reset.sql을 실행했는지 확인해 주세요.`);
  }
  return res;
}
