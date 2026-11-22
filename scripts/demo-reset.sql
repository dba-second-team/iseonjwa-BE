-- ============================================
-- scripts/demo-reset.sql
-- 예매 중인 공연(K-POP LIVE, HIPHOP & R&B NIGHT)을 시연 상태로 되돌린다
-- ============================================
-- 언제 실행하나
--   1. k6 동시성 테스트나 수동 테스트가 끝난 뒤
--   2. 발표 시연 직전 (선점 좌석이 5분 뒤 만료되니 시연 바로 전에)
-- 몇 번을 실행해도 결과가 같다.
-- V6까지 적용된 DB에서만 실행한다. Flyway 마이그레이션이 아니라 직접 실행하는 스크립트다.
--
-- 결제 상태와 좌석은 V6과 같은 규칙을 따른다.
--   PAID 예매 확정(좌석 RESERVED), CANCELED 환불 진행 중, REFUNDED 환불 완료 (취소된 예매의 좌석은 AVAILABLE)
--
-- 실행하면 생기는 상태
--   [K-POP LIVE 2027-01-16]
--     demo.user   확정 2석(F1 1열 1, 2번), 환불 진행 중 1건(S1 2열 1번, 좌석은 빈 좌석)
--     demo.limit  확정 3석(S1 1열 1~3번). 이 회차 한도 4매라 1석만 더 잡힌다
--     load301~340 확정 30건, 환불 진행 중 6건, 환불 완료 4건 (관리자 예매 목록, 환불 처리용)
--     G 1열 1~4번  다른 회원이 선점 중 (5분 뒤 만료)
--     G 1열 5, 6번 선점 시간이 지난 좌석 (다시 선점할 수 있어야 함)
--   [K-POP LIVE 2027-01-17]  전석 빈 좌석 (k6, 팀원 수동 테스트 대상)
--   [K-POP LIVE 2026-12-03]  demo.user 확정 1석 (발표 당일이라 취소 불가)
--   [HIPHOP 2027-02-06]      G 15열 9, 10번만 남은 매진 직전 회차. demo.user 확정 2석 포함
-- V3에서 넣은 예매 2건은 이 스크립트가 만든 예매로 바뀐다.

SET NAMES utf8mb4;  -- 한글 공연명을 비교하므로 클라이언트 문자셋을 맞춘다
-- 앱은 한국 시간(Asia/Seoul)으로 시각을 저장하고, RDS 기본 시간대는 UTC라 맞춰 준다
SET time_zone = '+09:00';
SET @now = NOW();

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
SELECT member_id INTO @load600 FROM member WHERE email = 'load600@example.com';

-- 하나라도 못 찾으면(V6 미적용, 공연명 변경 등) 아무것도 지우기 전에 일부러 에러를 내고 멈춘다
-- 에러 메시지는 "Subquery returns more than 1 row"로 나온다
SELECT IF(
    @kpop_main IS NULL OR @kpop_k6 IS NULL OR @kpop_today IS NULL OR @hiphop_main IS NULL
        OR @demo_user IS NULL OR @limit_user IS NULL OR @load199 IS NULL OR @load200 IS NULL
        OR @load600 IS NULL,
    (SELECT 1 UNION ALL SELECT 2),
    1
) INTO @targets_found;


-- ============================================
-- 2. 예매 목록 만들기
-- 예매 하나하나를 작업용 임시 테이블에 적고, 아래 4번에서 한 번에 예매, 좌석, 결제로 만든다
-- 임시 테이블은 트랜잭션 밖에서 만들고 지운다
--   ref         예매를 구분하는 번호 (예매와 좌석을 잇는 데 쓴다)
--   seat_from, seat_to  좌석을 (구역 F1~F4, S1, S2, G, 열, 번호) 순으로 세웠을 때의 순번 범위
-- ============================================

DROP TEMPORARY TABLE IF EXISTS demo_plan;
CREATE TEMPORARY TABLE demo_plan (
    ref INT NOT NULL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    reserved_at DATETIME NOT NULL,
    canceled_at DATETIME NULL,
    refunded_at DATETIME NULL,
    section VARCHAR(2) NULL,     -- NULL이면 회차 전체 좌석에서 순번을 센다
    row_num VARCHAR(10) NULL,    -- NULL이면 구역 전체에서 순번을 센다
    seat_from INT NOT NULL,
    seat_to INT NOT NULL
);

-- 2-1. demo.user, demo.limit (좌석 위치를 직접 지정: section, row_num, seat_num 범위)
INSERT INTO demo_plan VALUES
    (1, @demo_user, @kpop_main, 'PAID', @now - INTERVAL 3 DAY, NULL, NULL, 'F1', '1', 1, 2),
    (2, @demo_user, @kpop_main, 'CANCELED', @now - INTERVAL 5 DAY, @now - INTERVAL 4 DAY, NULL, 'S1', '2', 1, 1),
    (3, @demo_user, @kpop_today, 'PAID', @now - INTERVAL 2 DAY, NULL, NULL, 'F2', '1', 1, 1),
    (4, @limit_user, @kpop_main, 'PAID', @now - INTERVAL 1 DAY, NULL, NULL, 'S1', '1', 1, 3);

-- 2-2. K-POP LIVE 1/16 관리자용: load301~load340이 G구역 2열부터 2석씩
--   묶음 0~29 확정, 30~35 환불 진행 중, 36~39 환불 완료
INSERT INTO demo_plan
WITH RECURSIVE n AS (SELECT 0 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 39)
SELECT
    100 + n.i,
    m.member_id,
    @kpop_main,
    CASE WHEN n.i < 30 THEN 'PAID' WHEN n.i < 36 THEN 'CANCELED' ELSE 'REFUNDED' END,
    @now - INTERVAL (n.i + 1) * 3 HOUR - INTERVAL 6 DAY,
    IF(n.i < 30, NULL, @now - INTERVAL (n.i - 29) * 5 HOUR),
    IF(n.i < 36, NULL, @now - INTERVAL (n.i - 35) * 2 HOUR),
    'G',
    NULL,
    10 + n.i * 2 + 1,   -- G구역 1열(10석)은 선점 시연용이라 건너뛴다
    10 + n.i * 2 + 2
FROM n
JOIN member AS m ON m.email = CONCAT('load', 301 + n.i, '@example.com');

-- 2-3. HIPHOP 2/6 매진 직전: 회차 전체 1,050석 중 앞 1,048석을 2석씩 524건 (인당 한도 2)
--   묶음 0은 demo.user, 나머지는 load001~load523. 남는 좌석은 마지막 G 15열 9, 10번
INSERT INTO demo_plan
WITH RECURSIVE n AS (SELECT 0 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 523)
SELECT
    1000 + n.i,
    COALESCE(IF(n.i = 0, @demo_user, NULL), m.member_id),
    @hiphop_main,
    'PAID',
    @now - INTERVAL (n.i + 1) * 10 MINUTE,
    NULL,
    NULL,
    NULL,
    NULL,
    n.i * 2 + 1,
    n.i * 2 + 2
FROM n
LEFT JOIN member AS m ON m.email = CONCAT('load', LPAD(n.i, 3, '0'), '@example.com');


START TRANSACTION;


-- ============================================
-- 3. 예매 중인 공연의 예매, 결제, 선점을 모두 지운다
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
-- 4. 목록대로 예매, 좌석, 결제 만들기
-- ============================================

INSERT INTO reservation (status, reserved_at, canceled_at, member_id, schedule_id)
SELECT IF(payment_status = 'PAID', 'CONFIRMED', 'CANCELED'), reserved_at, canceled_at, member_id, schedule_id
FROM demo_plan
ORDER BY ref;

-- 좌석 순번: 지정한 구역, 열 안에서 (구역, 열, 번호) 순으로 센다
INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, st.seat_id
FROM demo_plan AS p
JOIN reservation AS r
    ON r.member_id = p.member_id
   AND r.schedule_id = p.schedule_id
   AND r.reserved_at = p.reserved_at
JOIN (
    SELECT seat_id, schedule_id, section, row_num, seat_num,
           ROW_NUMBER() OVER (PARTITION BY schedule_id
                              ORDER BY FIELD(section, 'F1', 'F2', 'F3', 'F4', 'S1', 'S2', 'G'),
                                       CAST(row_num AS UNSIGNED), seat_num) AS in_schedule,
           ROW_NUMBER() OVER (PARTITION BY schedule_id, section ORDER BY CAST(row_num AS UNSIGNED), seat_num) AS in_section
    FROM seat
    WHERE schedule_id IN (@kpop_main, @kpop_today, @hiphop_main)
) AS st
    ON st.schedule_id = p.schedule_id
   AND (
        (p.section IS NULL AND st.in_schedule BETWEEN p.seat_from AND p.seat_to)
     OR (p.section IS NOT NULL AND p.row_num IS NULL AND st.section = p.section
         AND st.in_section BETWEEN p.seat_from AND p.seat_to)
     OR (p.row_num IS NOT NULL AND st.section = p.section AND st.row_num = p.row_num
         AND st.seat_num BETWEEN p.seat_from AND p.seat_to)
   );

-- 수수료율: 취소일부터 공연 날짜까지 남은 일수 (#27 CancelFeePolicy와 같은 규칙)
INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT
    t.paid_amount,
    t.cancel_fee,
    IF(t.payment_status = 'PAID', 0, t.paid_amount - t.cancel_fee),
    t.payment_status,
    t.reserved_at + INTERVAL 1 MINUTE,
    t.refunded_at,
    t.reservation_id
FROM (
    SELECT
        r.reservation_id,
        p.payment_status,
        p.reserved_at,
        p.refunded_at,
        SUM(pr.price) AS paid_amount,
        CASE
            WHEN p.payment_status = 'PAID' THEN 0
            WHEN DATEDIFF(DATE(s.start_at), DATE(p.canceled_at)) >= 7 THEN 0
            WHEN DATEDIFF(DATE(s.start_at), DATE(p.canceled_at)) >= 3 THEN SUM(pr.price) * 10 DIV 100
            ELSE SUM(pr.price) * 30 DIV 100
        END AS cancel_fee
    FROM demo_plan AS p
    JOIN reservation AS r
        ON r.member_id = p.member_id
       AND r.schedule_id = p.schedule_id
       AND r.reserved_at = p.reserved_at
    JOIN schedule AS s ON s.schedule_id = r.schedule_id
    JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
    JOIN seat AS st ON st.seat_id = rs.seat_id
    JOIN price AS pr ON pr.performance_id = s.performance_id AND pr.section = st.section
    GROUP BY r.reservation_id, p.payment_status, p.reserved_at, p.refunded_at, p.canceled_at, s.start_at
) AS t;

-- 확정(PAID) 예매의 좌석만 RESERVED. 취소된 예매의 좌석은 취소 시점에 반환된다(DFD 10.0)
UPDATE seat
SET status = 'RESERVED'
WHERE seat_id IN (
    SELECT rs.seat_id
    FROM reservation_seat AS rs
    JOIN payment AS pay ON pay.reservation_id = rs.reservation_id
    JOIN reservation AS r ON r.reservation_id = rs.reservation_id
    WHERE pay.status = 'PAID'
      AND r.schedule_id IN (@kpop_main, @kpop_today, @hiphop_main)
);

-- ============================================
-- 5. 선점 상태: K-POP LIVE 1/16, G구역 1열
-- ============================================

UPDATE seat
SET status = 'HELD', held_by = @load199, held_until = @now + INTERVAL 5 MINUTE
WHERE schedule_id = @kpop_main AND section = 'G' AND row_num = '1' AND seat_num BETWEEN 1 AND 4;

UPDATE seat
SET status = 'HELD', held_by = @load200, held_until = @now - INTERVAL 1 MINUTE
WHERE schedule_id = @kpop_main AND section = 'G' AND row_num = '1' AND seat_num IN (5, 6);

COMMIT;

DROP TEMPORARY TABLE demo_plan;


-- ============================================
-- 6. 결과 확인
-- ============================================

SELECT s.start_at, p.title,
       SUM(st.status = 'AVAILABLE') AS available,
       SUM(st.status = 'HELD') AS held,
       SUM(st.status = 'RESERVED') AS reserved
FROM seat AS st
JOIN schedule AS s ON s.schedule_id = st.schedule_id
JOIN performance AS p ON p.performance_id = s.performance_id
WHERE p.performance_id IN (@kpop_id, @hiphop_id)
GROUP BY s.schedule_id, s.start_at, p.title
ORDER BY s.start_at;
