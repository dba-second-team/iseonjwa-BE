-- ============================================
-- scripts/demo-reset.sql
-- 예매 중인 공연(K-POP LIVE, HIPHOP & R&B NIGHT)을 시연 상태로 되돌린다
-- ============================================
-- 언제 실행하나
--   1. k6 동시성 테스트가 끝난 뒤
--   2. 발표 시연 직전 (선점 좌석이 5분 뒤 만료되니 시연 바로 전에)
-- 몇 번을 실행해도 결과가 같다.
-- V6까지 적용된 DB에서만 실행한다. Flyway 마이그레이션이 아니라 직접 실행하는 스크립트다.
--
-- 실행하면 생기는 상태
--   [K-POP LIVE 2027-01-16]  내 예매 2석, 취소된 예매 1건, 한도 직전 회원 3석,
--                            다른 회원이 선점 중인 좌석 4석, 선점이 만료된 좌석 2석
--   [K-POP LIVE 2027-01-17]  전석 빈 좌석 (k6 테스트 대상)
--   [K-POP LIVE 2026-12-03]  내 예매 1석 (발표 당일이라 취소 불가)
--   [HIPHOP 2027-02-06]      G구역 6열 15, 16번만 남은 매진 직전 회차, 내 예매 2석
-- 매진, 종료, 취소된 공연의 demo.user 예매는 V6에 있고 이 스크립트가 건드리지 않는다.
-- V3에서 넣은 예매 2건은 이 스크립트가 만든 예매로 바뀐다.

-- 앱은 한국 시간(Asia/Seoul)으로 시각을 저장하고, RDS 기본 시간대는 UTC라 맞춰 준다
SET NAMES utf8mb4;  -- 한글 공연명을 비교하므로 클라이언트 문자셋을 맞춘다
SET time_zone = '+09:00';
SET @now = NOW();

START TRANSACTION;


-- ============================================
-- 1. 대상 회차, 회원 ID 찾기
-- ============================================

SELECT performance_id INTO @kpop_id FROM performance WHERE title = '이선좌 K-POP LIVE';
SELECT performance_id INTO @hiphop_id FROM performance WHERE title = '이선좌 HIPHOP & R&B NIGHT';

SELECT schedule_id INTO @kpop_main
FROM schedule WHERE performance_id = @kpop_id AND start_at = '2027-01-16 18:00:00';
SELECT schedule_id INTO @kpop_k6
FROM schedule WHERE performance_id = @kpop_id AND start_at = '2027-01-17 18:00:00';
SELECT schedule_id INTO @kpop_today
FROM schedule WHERE performance_id = @kpop_id AND start_at = '2026-12-03 19:00:00';
SELECT schedule_id INTO @hiphop_main
FROM schedule WHERE performance_id = @hiphop_id AND start_at = '2027-02-06 19:00:00';

SELECT member_id INTO @demo_user FROM member WHERE email = 'demo.user@example.com';
SELECT member_id INTO @limit_user FROM member WHERE email = 'demo.limit@example.com';
SELECT member_id INTO @load199 FROM member WHERE email = 'load199@example.com';
SELECT member_id INTO @load200 FROM member WHERE email = 'load200@example.com';

-- 하나라도 못 찾으면(V6 미적용, 공연명 변경 등) 아무것도 지우기 전에 일부러 에러를 내고 멈춘다
-- 에러 메시지는 "Subquery returns more than 1 row"로 나온다
SELECT IF(
    @kpop_main IS NULL OR @kpop_k6 IS NULL OR @kpop_today IS NULL OR @hiphop_main IS NULL
        OR @demo_user IS NULL OR @limit_user IS NULL OR @load199 IS NULL OR @load200 IS NULL,
    (SELECT 1 UNION ALL SELECT 2),
    1
) INTO @targets_found;


-- ============================================
-- 2. 예매 중인 공연의 예매, 결제, 선점을 모두 지운다
-- ============================================

DELETE pay
FROM payment AS pay
JOIN reservation AS r ON r.reservation_id = pay.reservation_id
JOIN schedule AS s ON s.schedule_id = r.schedule_id
WHERE s.performance_id IN (@kpop_id, @hiphop_id);

DELETE rs
FROM reservation_seat AS rs
JOIN reservation AS r ON r.reservation_id = rs.reservation_id
JOIN schedule AS s ON s.schedule_id = r.schedule_id
WHERE s.performance_id IN (@kpop_id, @hiphop_id);

DELETE r
FROM reservation AS r
JOIN schedule AS s ON s.schedule_id = r.schedule_id
WHERE s.performance_id IN (@kpop_id, @hiphop_id);

UPDATE seat AS st
JOIN schedule AS s ON s.schedule_id = st.schedule_id
SET st.status = 'AVAILABLE', st.held_until = NULL, st.held_by = NULL
WHERE s.performance_id IN (@kpop_id, @hiphop_id);

-- k6 signup-race.js가 만든 회원
DELETE FROM member WHERE email LIKE 'k6-%@example.com';


-- ============================================
-- 3. demo.user의 예매 내역
-- ============================================

-- 3-1. 확정 예매: K-POP LIVE 1/16, F1구역 1열 1, 2번
INSERT INTO reservation (status, reserved_at, canceled_at, member_id, schedule_id)
VALUES ('CONFIRMED', @now - INTERVAL 3 DAY, NULL, @demo_user, @kpop_main);
SET @r = LAST_INSERT_ID();

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT @r, seat_id FROM seat
WHERE schedule_id = @kpop_main AND section = 'F1' AND row_num = '1' AND seat_num IN (1, 2);

UPDATE seat SET status = 'RESERVED'
WHERE seat_id IN (SELECT seat_id FROM reservation_seat WHERE reservation_id = @r);

INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT SUM(pr.price), 0, 0, 'PAID', @now - INTERVAL 3 DAY + INTERVAL 1 MINUTE, NULL, @r
FROM reservation_seat AS rs
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr ON pr.performance_id = @kpop_id AND pr.section = st.section
WHERE rs.reservation_id = @r;

-- 3-2. 취소된 예매: K-POP LIVE 1/16, S1구역 2열 1번 (좌석은 다시 빈 좌석)
-- 취소 수수료 10%는 시연용 가정값이다. 팀 정책이 정해지면 맞춰 바꾼다.
INSERT INTO reservation (status, reserved_at, canceled_at, member_id, schedule_id)
VALUES ('CANCELED', @now - INTERVAL 5 DAY, @now - INTERVAL 4 DAY, @demo_user, @kpop_main);
SET @r = LAST_INSERT_ID();

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT @r, seat_id FROM seat
WHERE schedule_id = @kpop_main AND section = 'S1' AND row_num = '2' AND seat_num = 1;

INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT SUM(pr.price), SUM(pr.price) DIV 10, SUM(pr.price) - SUM(pr.price) DIV 10,
       'REFUNDED', @now - INTERVAL 5 DAY + INTERVAL 1 MINUTE, @now - INTERVAL 4 DAY, @r
FROM reservation_seat AS rs
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr ON pr.performance_id = @kpop_id AND pr.section = st.section
WHERE rs.reservation_id = @r;

-- 3-3. 발표 당일 회차 예매: K-POP LIVE 12/3, F2구역 1열 1번 (당일 취소 불가 시연)
INSERT INTO reservation (status, reserved_at, canceled_at, member_id, schedule_id)
VALUES ('CONFIRMED', @now - INTERVAL 2 DAY, NULL, @demo_user, @kpop_today);
SET @r = LAST_INSERT_ID();

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT @r, seat_id FROM seat
WHERE schedule_id = @kpop_today AND section = 'F2' AND row_num = '1' AND seat_num = 1;

UPDATE seat SET status = 'RESERVED'
WHERE seat_id IN (SELECT seat_id FROM reservation_seat WHERE reservation_id = @r);

INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT SUM(pr.price), 0, 0, 'PAID', @now - INTERVAL 2 DAY + INTERVAL 1 MINUTE, NULL, @r
FROM reservation_seat AS rs
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr ON pr.performance_id = @kpop_id AND pr.section = st.section
WHERE rs.reservation_id = @r;


-- ============================================
-- 4. 한도 직전 회원: K-POP LIVE(한도 4매) 1/16에 3석 예매
-- 이 회원으로 1석은 더 잡히고, 2석째에서 HOLD_LIMIT_EXCEEDED가 나야 한다
-- ============================================

INSERT INTO reservation (status, reserved_at, canceled_at, member_id, schedule_id)
VALUES ('CONFIRMED', @now - INTERVAL 1 DAY, NULL, @limit_user, @kpop_main);
SET @r = LAST_INSERT_ID();

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT @r, seat_id FROM seat
WHERE schedule_id = @kpop_main AND section = 'S1' AND row_num = '1' AND seat_num IN (1, 2, 3);

UPDATE seat SET status = 'RESERVED'
WHERE seat_id IN (SELECT seat_id FROM reservation_seat WHERE reservation_id = @r);

INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT SUM(pr.price), 0, 0, 'PAID', @now - INTERVAL 1 DAY + INTERVAL 1 MINUTE, NULL, @r
FROM reservation_seat AS rs
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr ON pr.performance_id = @kpop_id AND pr.section = st.section
WHERE rs.reservation_id = @r;


-- ============================================
-- 5. 선점 상태: K-POP LIVE 1/16, G구역 1열
--   1~4번: 다른 회원이 선점 중 (5분 뒤 만료)
--   5~6번: 선점 시간이 이미 지난 좌석 (다시 선점할 수 있어야 한다)
-- ============================================

UPDATE seat
SET status = 'HELD', held_by = @load199, held_until = @now + INTERVAL 5 MINUTE
WHERE schedule_id = @kpop_main AND section = 'G' AND row_num = '1' AND seat_num BETWEEN 1 AND 4;

UPDATE seat
SET status = 'HELD', held_by = @load200, held_until = @now - INTERVAL 1 MINUTE
WHERE schedule_id = @kpop_main AND section = 'G' AND row_num = '1' AND seat_num IN (5, 6);


-- ============================================
-- 6. 매진 직전 회차: HIPHOP 2/6, G구역 6열 15, 16번만 남김
-- 나머지 302석을 2석씩 묶어 demo.user와 부하 테스트 회원 150명(load002~load151)이 예매 (인당 한도 2)
-- reserved_at을 묶음마다 다르게 해서 예매와 좌석을 연결하는 키로 쓴다
-- ============================================

INSERT INTO reservation (status, reserved_at, canceled_at, member_id, schedule_id)
SELECT 'CONFIRMED', @now - INTERVAL (g.grp + 1) * 10 MINUTE, NULL, m.member_id, @hiphop_main
FROM (
    SELECT DISTINCT (ROW_NUMBER() OVER (ORDER BY section, CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 2 AS grp
    FROM seat
    WHERE schedule_id = @hiphop_main
      AND NOT (section = 'G' AND row_num = '6' AND seat_num IN (15, 16))
) AS g
JOIN member AS m ON m.email = CONCAT('load', LPAD(g.grp + 1, 3, '0'), '@example.com');

-- 첫 묶음(2석)은 demo.user가 예매한 것으로 바꾼다
UPDATE reservation
SET member_id = @demo_user
WHERE schedule_id = @hiphop_main
  AND reserved_at = @now - INTERVAL 10 MINUTE;

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, t.seat_id
FROM (
    SELECT seat_id, (ROW_NUMBER() OVER (ORDER BY section, CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 2 AS grp
    FROM seat
    WHERE schedule_id = @hiphop_main
      AND NOT (section = 'G' AND row_num = '6' AND seat_num IN (15, 16))
) AS t
JOIN reservation AS r
    ON r.schedule_id = @hiphop_main
   AND r.reserved_at = @now - INTERVAL (t.grp + 1) * 10 MINUTE;

UPDATE seat SET status = 'RESERVED'
WHERE seat_id IN (
    SELECT rs.seat_id
    FROM reservation_seat AS rs
    JOIN reservation AS r ON r.reservation_id = rs.reservation_id
    WHERE r.schedule_id = @hiphop_main
);

INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT SUM(pr.price), 0, 0, 'PAID', r.reserved_at + INTERVAL 1 MINUTE, NULL, r.reservation_id
FROM reservation AS r
JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr ON pr.performance_id = @hiphop_id AND pr.section = st.section
WHERE r.schedule_id = @hiphop_main
GROUP BY r.reservation_id, r.reserved_at;

COMMIT;


-- ============================================
-- 7. 결과 확인
-- ============================================

SELECT s.schedule_id, s.start_at, p.title,
       SUM(st.status = 'AVAILABLE') AS available,
       SUM(st.status = 'HELD') AS held,
       SUM(st.status = 'RESERVED') AS reserved
FROM seat AS st
JOIN schedule AS s ON s.schedule_id = st.schedule_id
JOIN performance AS p ON p.performance_id = s.performance_id
WHERE p.performance_id IN (@kpop_id, @hiphop_id)
GROUP BY s.schedule_id, s.start_at, p.title
ORDER BY s.start_at;
