-- ============================================
-- k6 실행 후 DB가 정합한지 확인
-- 1~3번은 0행이 나와야 정상이다. 한 행이라도 나오면 동시성 버그다.
-- ============================================

SET NAMES utf8mb4;  -- 한글 공연명을 비교하므로 클라이언트 문자셋을 맞춘다
SET time_zone = '+09:00';  -- 앱이 한국 시간으로 저장하므로 NOW()도 한국 시간으로

-- 1. 같은 좌석이 취소되지 않은 예매 두 건 이상에 묶여 있다 (중복 예매)
SELECT rs.seat_id, COUNT(*) AS active_reservations
FROM reservation_seat AS rs
JOIN reservation AS r ON r.reservation_id = rs.reservation_id
WHERE r.status <> 'CANCELED'
GROUP BY rs.seat_id
HAVING COUNT(*) > 1;

-- 2. 좌석 상태와 선점 정보가 맞지 않는다
--    HELD인데 선점한 회원이나 만료 시각이 없거나, HELD가 아닌데 선점 정보가 남아 있다
SELECT seat_id, status, held_by, held_until
FROM seat
WHERE (status = 'HELD' AND (held_by IS NULL OR held_until IS NULL))
   OR (status <> 'HELD' AND (held_by IS NOT NULL OR held_until IS NOT NULL));

-- 3. 회원 한 명이 한 공연에서 한도보다 많이 선점하거나 예매했다
--    (팀이 한도를 회차 기준으로 정하면 GROUP BY에 s.schedule_id를 넣는다)
SELECT member_id, performance_id, max_tickets_per_member, SUM(cnt) AS total
FROM (
    -- 선점 중 (만료 전)
    SELECT st.held_by AS member_id, s.performance_id, COUNT(*) AS cnt
    FROM seat AS st
    JOIN schedule AS s ON s.schedule_id = st.schedule_id
    WHERE st.status = 'HELD' AND st.held_until > NOW()
    GROUP BY st.held_by, s.performance_id
    UNION ALL
    -- 예매 (취소 제외)
    SELECT r.member_id, s.performance_id, COUNT(*)
    FROM reservation AS r
    JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
    JOIN schedule AS s ON s.schedule_id = r.schedule_id
    WHERE r.status <> 'CANCELED'
    GROUP BY r.member_id, s.performance_id
) AS t
JOIN performance AS p USING (performance_id)
GROUP BY member_id, performance_id, max_tickets_per_member
HAVING SUM(cnt) > max_tickets_per_member;

-- 4. k6 대상 회차 요약 (테스트 결과 캡처용)
SELECT st.seat_id, st.section, st.row_num, st.seat_num, st.status, st.held_by, st.held_until
FROM seat AS st
JOIN schedule AS s ON s.schedule_id = st.schedule_id
JOIN performance AS p ON p.performance_id = s.performance_id
WHERE p.title = '이선좌 K-POP LIVE'
  AND s.start_at = '2027-01-17 18:00:00'
  AND st.status <> 'AVAILABLE'
ORDER BY st.seat_id;
