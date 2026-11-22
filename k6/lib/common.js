import http from 'k6/http';
import { sleep } from 'k6';

export const BASE = __ENV.BASE || 'http://localhost:8080';
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
    return res.json('code');
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
// 좌석 선점 API 호출
// 아직 main에 선점 API가 없어서 경로와 요청 형식은 가정이다.
// API가 머지되면 이 함수만 실제 명세에 맞게 고치면 된다.
//   가정: POST /api/schedules/{scheduleId}/seats/hold
//         헤더 X-Member-Id, 본문 {"seatIds": [1, 2]}
//         성공 200 또는 201, 실패 409 {"code": "SEAT_UNAVAILABLE" | "HOLD_LIMIT_EXCEEDED", ...}
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
