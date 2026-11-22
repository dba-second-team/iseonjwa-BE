-- ============================================
-- k6에 넘길 ID 찾기
-- 대상: K-POP LIVE 2027-01-17 회차 (demo-reset.sql 실행 후 전석 빈 좌석)
-- ============================================

SET NAMES utf8mb4;  -- 한글 공연명을 비교하므로 클라이언트 문자셋을 맞춘다
SELECT s.schedule_id AS SCHEDULE_ID
FROM schedule AS s
JOIN performance AS p ON p.performance_id = s.performance_id
WHERE p.title = '이선좌 K-POP LIVE'
  AND s.start_at = '2027-01-17 18:00:00';

-- seat-hold-race.js: 100명이 다툴 좌석 1개 (F1구역 1열 1번)
-- deadlock.js: 앞의 두 좌석 (F1구역 1열 1, 2번)
-- hold-limit.js: 서로 다른 좌석 10개 (G구역 1열 1~10번)
SELECT
    MAX(CASE WHEN st.section = 'F1' AND st.row_num = '1' AND st.seat_num = 1 THEN st.seat_id END) AS SEAT_ID,
    CONCAT_WS(',',
        MAX(CASE WHEN st.section = 'F1' AND st.row_num = '1' AND st.seat_num = 1 THEN st.seat_id END),
        MAX(CASE WHEN st.section = 'F1' AND st.row_num = '1' AND st.seat_num = 2 THEN st.seat_id END)
    ) AS SEAT_PAIR,
    GROUP_CONCAT(
        CASE WHEN st.section = 'G' AND st.row_num = '1' AND st.seat_num <= 10 THEN st.seat_id END
        ORDER BY st.seat_id
    ) AS SEAT_IDS
FROM seat AS st
JOIN schedule AS s ON s.schedule_id = st.schedule_id
JOIN performance AS p ON p.performance_id = s.performance_id
WHERE p.title = '이선좌 K-POP LIVE'
  AND s.start_at = '2027-01-17 18:00:00';
