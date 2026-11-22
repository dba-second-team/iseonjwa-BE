-- ============================================
-- V6__add_demo_seed.sql
-- 시연과 동시성 테스트에 쓰는 기본 데이터
-- ============================================
-- 여기에는 시간이 지나도 바뀌지 않는 데이터만 둔다.
--   회원, 공연, 가격, 회차, 좌석 배치, 지난 예매 기록(매진 공연, 종료 공연, 취소된 공연),
--   공연 목록 스크롤 테스트용 공연 48개
-- 시연 계정 demo.user는 예매 내역 49건을 가진다(내 예매 내역 무한 스크롤용).
-- 선점 중인 좌석, 매진 직전 회차, 시연용 예매처럼 시간에 따라 달라지는 상태는
-- scripts/demo-reset.sql 에서 만든다.
--
-- 테스트 계정 비밀번호: 모두 Demo1234!
-- 좌석 배치(회차마다 같음, 총 304석)
--   F1~F4: 4열 x 8석   (가격은 FS 등급과 같음)
--   S1, S2: 4열 x 10석 (가격은 VIP 등급과 같음)
--   G:      6열 x 16석 (가격은 R 등급과 같음)


-- ============================================
-- 1. MEMBER
-- 한도 테스트 회원 1명, 팀원 수동 테스트 계정 5명, 부하 테스트 회원 200명
-- ============================================

SET @demo_password = '$2a$10$.tD8wL83k7K3LGd0S/GQ7u8sYawsYeALHikOFn4RfdKrKdtgEy55u'; -- Demo1234!

INSERT INTO member
(email, name, phone, role, created_at, password)
VALUES
    ('demo.limit@example.com', '한도테스트회원', '01011112222', 'USER', '2026-11-16 09:00:00', @demo_password),
    ('team1@example.com', '팀원1', NULL, 'USER', '2026-11-16 09:00:00', @demo_password),
    ('team2@example.com', '팀원2', NULL, 'USER', '2026-11-16 09:00:00', @demo_password),
    ('team3@example.com', '팀원3', NULL, 'USER', '2026-11-16 09:00:00', @demo_password),
    ('team4@example.com', '팀원4', NULL, 'USER', '2026-11-16 09:00:00', @demo_password),
    ('team5@example.com', '팀원5', NULL, 'USER', '2026-11-16 09:00:00', @demo_password);

-- load001@example.com ~ load200@example.com
INSERT INTO member
(email, name, phone, role, created_at, password)
WITH RECURSIVE n AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < 200
)
SELECT
    CONCAT('load', LPAD(i, 3, '0'), '@example.com'),
    CONCAT('부하테스트', LPAD(i, 3, '0')),
    NULL,
    'USER',
    '2026-11-16 09:00:00',
    @demo_password
FROM n;


-- ============================================
-- 2. PERFORMANCE
-- 기존 공연 2개는 ON_SALE이라, 나머지 상태를 하나씩 추가
-- ============================================

INSERT INTO performance
(title, genre, running_time, description, status, venue_name, max_tickets_per_member)
VALUES ('이선좌 발라드 콘서트', 'VALLAD', 110, '예매 오픈 전 공연', 'UPCOMING', '이선좌 공연장 C', 4);
SET @upcoming_id = LAST_INSERT_ID();

INSERT INTO performance
(title, genre, running_time, description, status, venue_name, max_tickets_per_member)
VALUES ('이선좌 K-POP 앵콜', 'K_POP', 120, '전석 매진된 공연', 'SOLD_OUT', '이선좌 공연장 D', 4);
SET @sold_out_id = LAST_INSERT_ID();

INSERT INTO performance
(title, genre, running_time, description, status, venue_name, max_tickets_per_member)
VALUES ('이선좌 가을 페스티벌', 'ETC', 180, '이미 끝난 공연', 'CLOSED', '이선좌 공연장 B', 4);
SET @closed_id = LAST_INSERT_ID();

INSERT INTO performance
(title, genre, running_time, description, status, venue_name, max_tickets_per_member)
VALUES ('이선좌 겨울 힙합 쇼', 'HIPHOP_RANDB', 100, '주최 측 사정으로 취소된 공연', 'CANCELED', '이선좌 공연장 C', 2);
SET @canceled_id = LAST_INSERT_ID();


-- ============================================
-- 3. PRICE
-- 구역별 가격 (V4 이후 가격의 키는 performance_id + section)
-- ============================================

INSERT INTO price (performance_id, section, price)
SELECT p.performance_id, s.section, s.price
FROM (
    SELECT @upcoming_id AS performance_id
    UNION ALL SELECT @sold_out_id
    UNION ALL SELECT @closed_id
    UNION ALL SELECT @canceled_id
) AS p
CROSS JOIN (
    SELECT 'F1' AS section, 170000 AS price
    UNION ALL SELECT 'F2', 170000
    UNION ALL SELECT 'F3', 170000
    UNION ALL SELECT 'F4', 170000
    UNION ALL SELECT 'S1', 140000
    UNION ALL SELECT 'S2', 140000
    UNION ALL SELECT 'G', 110000
) AS s;


-- ============================================
-- 4. SCHEDULE
-- ============================================

-- 기존 K-POP LIVE에 발표 당일 회차 추가 (당일 취소 불가 시연용)
SELECT performance_id INTO @kpop_id
FROM performance
WHERE title = '이선좌 K-POP LIVE';

INSERT INTO schedule (start_at, performance_id)
VALUES
    ('2026-12-03 19:00:00', @kpop_id),
    ('2027-03-06 18:00:00', @upcoming_id),
    ('2027-03-07 18:00:00', @upcoming_id),
    ('2027-01-30 18:00:00', @sold_out_id),
    ('2026-10-18 17:00:00', @closed_id),
    ('2027-03-20 19:00:00', @canceled_id);

SELECT schedule_id INTO @sold_out_schedule_id
FROM schedule
WHERE performance_id = @sold_out_id;

SELECT schedule_id INTO @closed_schedule_id
FROM schedule
WHERE performance_id = @closed_id;


-- ============================================
-- 5. SEAT
-- 모든 회차에 같은 배치로 좌석 생성
-- V3에서 이미 만든 좌석(F1 1열 1번 등)은 건너뛴다
-- ============================================

INSERT INTO seat
(section, row_num, seat_num, status, held_until, held_by, schedule_id)
WITH RECURSIVE n AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < 16
)
SELECT layout.section, CAST(r.i AS CHAR), c.i, 'AVAILABLE', NULL, NULL, s.schedule_id
FROM schedule AS s
CROSS JOIN (
    SELECT 'F1' AS section, 4 AS row_count, 8 AS seat_count
    UNION ALL SELECT 'F2', 4, 8
    UNION ALL SELECT 'F3', 4, 8
    UNION ALL SELECT 'F4', 4, 8
    UNION ALL SELECT 'S1', 4, 10
    UNION ALL SELECT 'S2', 4, 10
    UNION ALL SELECT 'G', 6, 16
) AS layout
JOIN n AS r ON r.i <= layout.row_count
JOIN n AS c ON c.i <= layout.seat_count
WHERE NOT EXISTS (
    SELECT 1
    FROM seat AS existing
    WHERE existing.schedule_id = s.schedule_id
      AND existing.section = layout.section
      AND existing.row_num = CAST(r.i AS CHAR)
      AND existing.seat_num = c.i
);


-- ============================================
-- 6. 매진 공연 예매 기록
-- 304석을 4석씩 묶어 demo.user와 부하 테스트 회원 75명이 예매 (인당 한도 4)
-- 예매 시각을 6시간 간격으로 흩어 관리자 통계 그래프에 쓰도록 함
-- reserved_at은 묶음마다 달라서, 예매와 좌석을 연결하는 키로도 쓴다
-- ============================================

SET @sold_out_base = TIMESTAMP('2026-11-01 10:00:00');

INSERT INTO reservation
(status, reserved_at, canceled_at, member_id, schedule_id)
SELECT
    'CONFIRMED',
    @sold_out_base + INTERVAL g.grp * 6 HOUR,
    NULL,
    m.member_id,
    @sold_out_schedule_id
FROM (
    SELECT DISTINCT (ROW_NUMBER() OVER (ORDER BY section, CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 4 AS grp
    FROM seat
    WHERE schedule_id = @sold_out_schedule_id
) AS g
JOIN member AS m
    ON m.email = CONCAT('load', LPAD(g.grp + 1, 3, '0'), '@example.com');

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, t.seat_id
FROM (
    SELECT seat_id, (ROW_NUMBER() OVER (ORDER BY section, CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 4 AS grp
    FROM seat
    WHERE schedule_id = @sold_out_schedule_id
) AS t
JOIN reservation AS r
    ON r.schedule_id = @sold_out_schedule_id
   AND r.reserved_at = @sold_out_base + INTERVAL t.grp * 6 HOUR;

-- 첫 묶음(4석)은 시연 계정 demo.user가 예매한 것으로 바꾼다
SELECT member_id INTO @demo_user
FROM member
WHERE email = 'demo.user@example.com';

UPDATE reservation
SET member_id = @demo_user
WHERE schedule_id = @sold_out_schedule_id
  AND reserved_at = @sold_out_base;


-- ============================================
-- 7. 종료 공연 예매 기록
-- G구역 앞 60석을 2석씩 묶어 demo.user와 부하 테스트 회원 29명이 예매
-- ============================================

SET @closed_base = TIMESTAMP('2026-09-20 10:00:00');

INSERT INTO reservation
(status, reserved_at, canceled_at, member_id, schedule_id)
SELECT
    'CONFIRMED',
    @closed_base + INTERVAL g.grp * 12 HOUR,
    NULL,
    m.member_id,
    @closed_schedule_id
FROM (
    SELECT DISTINCT grp
    FROM (
        SELECT (ROW_NUMBER() OVER (ORDER BY section, CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 2 AS grp
        FROM seat
        WHERE schedule_id = @closed_schedule_id
          AND section = 'G'
    ) AS numbered
    WHERE grp < 30
) AS g
JOIN member AS m
    ON m.email = CONCAT('load', LPAD(g.grp + 1, 3, '0'), '@example.com');

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, t.seat_id
FROM (
    SELECT seat_id, (ROW_NUMBER() OVER (ORDER BY section, CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 2 AS grp
    FROM seat
    WHERE schedule_id = @closed_schedule_id
      AND section = 'G'
) AS t
JOIN reservation AS r
    ON r.schedule_id = @closed_schedule_id
   AND r.reserved_at = @closed_base + INTERVAL t.grp * 12 HOUR
WHERE t.grp < 30;

-- 첫 묶음(2석)은 demo.user가 예매한 것으로 바꾼다 (관람 완료 내역)
UPDATE reservation
SET member_id = @demo_user
WHERE schedule_id = @closed_schedule_id
  AND reserved_at = @closed_base;


-- ============================================
-- 8. 예매된 좌석 상태 변경과 결제
-- ============================================

UPDATE seat
SET status = 'RESERVED'
WHERE seat_id IN (
    SELECT rs.seat_id
    FROM reservation_seat AS rs
    JOIN reservation AS r ON r.reservation_id = rs.reservation_id
    WHERE r.schedule_id IN (@sold_out_schedule_id, @closed_schedule_id)
);

-- 결제 금액 = 예매한 좌석의 구역별 가격 합
INSERT INTO payment
(paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT
    SUM(pr.price),
    0,
    0,
    'PAID',
    r.reserved_at + INTERVAL 1 MINUTE,
    NULL,
    r.reservation_id
FROM reservation AS r
JOIN schedule AS sc ON sc.schedule_id = r.schedule_id
JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr
    ON pr.performance_id = sc.performance_id
   AND pr.section = st.section
WHERE r.schedule_id IN (@sold_out_schedule_id, @closed_schedule_id)
GROUP BY r.reservation_id, r.reserved_at;


-- ============================================
-- 9. 취소된 공연의 예매 기록
-- demo.user가 예매한 공연이 주최 측 사정으로 취소되어 전액 환불됨 (수수료 0)
-- 좌석은 빈 좌석으로 남는다
-- ============================================

SELECT schedule_id INTO @canceled_schedule_id
FROM schedule
WHERE performance_id = @canceled_id;

INSERT INTO reservation
(status, reserved_at, canceled_at, member_id, schedule_id)
VALUES ('CANCELED', '2026-11-05 20:00:00', '2026-11-18 10:00:00', @demo_user, @canceled_schedule_id);
SET @canceled_reservation_id = LAST_INSERT_ID();

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT @canceled_reservation_id, seat_id
FROM seat
WHERE schedule_id = @canceled_schedule_id
  AND section = 'S1'
  AND row_num = '1'
  AND seat_num IN (5, 6);

INSERT INTO payment
(paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT
    SUM(pr.price),
    0,
    SUM(pr.price),
    'REFUNDED',
    '2026-11-05 20:01:00',
    '2026-11-18 10:00:00',
    @canceled_reservation_id
FROM reservation_seat AS rs
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr
    ON pr.performance_id = @canceled_id
   AND pr.section = st.section
WHERE rs.reservation_id = @canceled_reservation_id;

-- ============================================
-- 10. 공연 목록 무한 스크롤(더 불러오기) 테스트용 공연 48개
-- ============================================
-- 상태: ON_SALE 36개, UPCOMING 6개, CLOSED 6개 (위 공연과 합치면 54개)
-- 장르 4개를 번갈아 넣어 목록이 한 장르로 몰리지 않게 했다.
-- 회차 날짜를 공연마다 다르게 잡아 같은 공연장에서 시간이 겹치는 회차가 없다.
-- 이 공연들의 demo.user 예매 기록은 11번에서 넣는다.

SELECT COALESCE(MAX(performance_id), 0) INTO @before_max FROM performance;


-- ============================================
-- 10-1. 스크롤용 PERFORMANCE
-- ============================================

INSERT INTO performance
(title, genre, running_time, description, status, venue_name, max_tickets_per_member)
VALUES
    ('별빛 쇼케이스', 'K_POP', 90, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 2),
    ('언더그라운드 사이퍼', 'HIPHOP_RANDB', 100, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 4),
    ('겨울 편지', 'VALLAD', 110, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 G', 4),
    ('재즈 인 더 시티', 'ETC', 120, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 H', 2),
    ('루키 데뷔 콘서트', 'K_POP', 150, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 4),
    ('소울 앤 그루브', 'HIPHOP_RANDB', 180, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 4),
    ('오래된 노래', 'VALLAD', 90, '예매 오픈 전 공연', 'UPCOMING', '이선좌 공연장 G', 2),
    ('클래식 갈라', 'ETC', 100, '이미 끝난 공연', 'CLOSED', '이선좌 공연장 H', 4),
    ('네온 시티 라이브', 'K_POP', 110, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 4),
    ('비트 메이커스 나이트', 'HIPHOP_RANDB', 120, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 2),
    ('그리움의 온도', 'VALLAD', 150, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 G', 4),
    ('인디 밴드 페스티벌', 'ETC', 180, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 H', 4),
    ('아이돌 페스타', 'K_POP', 90, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 2),
    ('미드나잇 R&B', 'HIPHOP_RANDB', 100, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 4),
    ('피아노와 목소리', 'VALLAD', 110, '예매 오픈 전 공연', 'UPCOMING', '이선좌 공연장 G', 4),
    ('어쿠스틱 피크닉', 'ETC', 120, '이미 끝난 공연', 'CLOSED', '이선좌 공연장 H', 2),
    ('드림 스테이지', 'K_POP', 150, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 4),
    ('랩 배틀 파이널', 'HIPHOP_RANDB', 180, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 4),
    ('별이 지는 밤', 'VALLAD', 90, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 G', 2),
    ('뮤지컬 넘버 콘서트', 'ETC', 100, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 H', 4),
    ('퍼플 나이트', 'K_POP', 110, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 4),
    ('스트릿 사운드', 'HIPHOP_RANDB', 120, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 2),
    ('다시 봄', 'VALLAD', 150, '예매 오픈 전 공연', 'UPCOMING', '이선좌 공연장 G', 4),
    ('록 스피릿', 'ETC', 180, '이미 끝난 공연', 'CLOSED', '이선좌 공연장 H', 4),
    ('스타트라인 콘서트', 'K_POP', 90, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 2),
    ('그루브 시티', 'HIPHOP_RANDB', 100, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 4),
    ('작은 위로', 'VALLAD', 110, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 G', 4),
    ('시네마 OST 콘서트', 'ETC', 120, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 H', 2),
    ('하이라이트 리런', 'K_POP', 150, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 4),
    ('블루 노트 세션', 'HIPHOP_RANDB', 180, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 4),
    ('우리의 이야기', 'VALLAD', 90, '예매 오픈 전 공연', 'UPCOMING', '이선좌 공연장 G', 2),
    ('국악 퓨전 무대', 'ETC', 100, '이미 끝난 공연', 'CLOSED', '이선좌 공연장 H', 4),
    ('첫눈 콘서트', 'K_POP', 110, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 4),
    ('크루 쇼케이스', 'HIPHOP_RANDB', 120, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 2),
    ('고요한 밤의 노래', 'VALLAD', 150, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 G', 4),
    ('일렉트로닉 나이트', 'ETC', 180, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 H', 4),
    ('뉴이어 카운트다운', 'K_POP', 90, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 2),
    ('플로우 페스티벌', 'HIPHOP_RANDB', 100, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 4),
    ('기억의 정원', 'VALLAD', 110, '예매 오픈 전 공연', 'UPCOMING', '이선좌 공연장 G', 4),
    ('아카펠라 하모니', 'ETC', 120, '이미 끝난 공연', 'CLOSED', '이선좌 공연장 H', 2),
    ('팬미팅 우리의 계절', 'K_POP', 150, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 4),
    ('레이트 나이트 잼', 'HIPHOP_RANDB', 180, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 4),
    ('손편지 콘서트', 'VALLAD', 90, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 G', 2),
    ('오케스트라 신년 음악회', 'ETC', 100, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 H', 4),
    ('글로우 업 투어', 'K_POP', 110, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 E', 4),
    ('붐뱁 리턴즈', 'HIPHOP_RANDB', 120, '예매 중인 공연', 'ON_SALE', '이선좌 공연장 F', 2),
    ('따뜻한 겨울', 'VALLAD', 150, '예매 오픈 전 공연', 'UPCOMING', '이선좌 공연장 G', 4),
    ('코미디 뮤직 쇼', 'ETC', 180, '이미 끝난 공연', 'CLOSED', '이선좌 공연장 H', 4);


-- ============================================
-- 10-2. 스크롤용 PRICE
-- 장르별로 가격대를 다르게 둔다
-- ============================================

INSERT INTO price (performance_id, section, price)
SELECT p.performance_id, s.section,
       s.base_price + CASE p.genre
           WHEN 'K_POP' THEN 20000
           WHEN 'HIPHOP_RANDB' THEN 0
           WHEN 'VALLAD' THEN -10000
           ELSE -30000
       END
FROM performance AS p
CROSS JOIN (
    SELECT 'F1' AS section, 160000 AS base_price
    UNION ALL SELECT 'F2', 160000
    UNION ALL SELECT 'F3', 160000
    UNION ALL SELECT 'F4', 160000
    UNION ALL SELECT 'S1', 130000
    UNION ALL SELECT 'S2', 130000
    UNION ALL SELECT 'G', 100000
) AS s
WHERE p.performance_id > @before_max;


-- ============================================
-- 10-3. 스크롤용 SCHEDULE
-- ============================================

INSERT INTO schedule (start_at, performance_id)
SELECT v.start_at, p.performance_id
FROM (
    SELECT '2026-12-10 19:00:00' AS start_at, '별빛 쇼케이스' AS title
    UNION ALL SELECT '2026-12-12 18:00:00', '언더그라운드 사이퍼'
    UNION ALL SELECT '2026-12-13 18:00:00', '언더그라운드 사이퍼'
    UNION ALL SELECT '2026-12-14 19:00:00', '겨울 편지'
    UNION ALL SELECT '2026-12-16 18:00:00', '재즈 인 더 시티'
    UNION ALL SELECT '2026-12-18 19:00:00', '루키 데뷔 콘서트'
    UNION ALL SELECT '2026-12-19 19:00:00', '루키 데뷔 콘서트'
    UNION ALL SELECT '2026-12-20 18:00:00', '소울 앤 그루브'
    UNION ALL SELECT '2027-05-13 19:00:00', '오래된 노래'
    UNION ALL SELECT '2026-09-08 18:00:00', '클래식 갈라'
    UNION ALL SELECT '2026-12-26 19:00:00', '네온 시티 라이브'
    UNION ALL SELECT '2026-12-28 18:00:00', '비트 메이커스 나이트'
    UNION ALL SELECT '2026-12-30 19:00:00', '그리움의 온도'
    UNION ALL SELECT '2026-12-31 19:00:00', '그리움의 온도'
    UNION ALL SELECT '2027-01-01 18:00:00', '인디 밴드 페스티벌'
    UNION ALL SELECT '2027-01-03 19:00:00', '아이돌 페스타'
    UNION ALL SELECT '2027-01-05 18:00:00', '미드나잇 R&B'
    UNION ALL SELECT '2027-01-06 18:00:00', '미드나잇 R&B'
    UNION ALL SELECT '2027-05-29 19:00:00', '피아노와 목소리'
    UNION ALL SELECT '2026-09-16 18:00:00', '어쿠스틱 피크닉'
    UNION ALL SELECT '2027-01-11 19:00:00', '드림 스테이지'
    UNION ALL SELECT '2027-01-12 19:00:00', '드림 스테이지'
    UNION ALL SELECT '2027-01-13 18:00:00', '랩 배틀 파이널'
    UNION ALL SELECT '2027-01-15 19:00:00', '별이 지는 밤'
    UNION ALL SELECT '2027-01-17 18:00:00', '뮤지컬 넘버 콘서트'
    UNION ALL SELECT '2027-01-18 18:00:00', '뮤지컬 넘버 콘서트'
    UNION ALL SELECT '2027-01-19 19:00:00', '퍼플 나이트'
    UNION ALL SELECT '2027-01-21 18:00:00', '스트릿 사운드'
    UNION ALL SELECT '2027-06-14 19:00:00', '다시 봄'
    UNION ALL SELECT '2027-06-15 19:00:00', '다시 봄'
    UNION ALL SELECT '2026-09-24 18:00:00', '록 스피릿'
    UNION ALL SELECT '2027-01-27 19:00:00', '스타트라인 콘서트'
    UNION ALL SELECT '2027-01-29 18:00:00', '그루브 시티'
    UNION ALL SELECT '2027-01-30 18:00:00', '그루브 시티'
    UNION ALL SELECT '2027-01-31 19:00:00', '작은 위로'
    UNION ALL SELECT '2027-02-02 18:00:00', '시네마 OST 콘서트'
    UNION ALL SELECT '2027-02-04 19:00:00', '하이라이트 리런'
    UNION ALL SELECT '2027-02-05 19:00:00', '하이라이트 리런'
    UNION ALL SELECT '2027-02-06 18:00:00', '블루 노트 세션'
    UNION ALL SELECT '2027-06-30 19:00:00', '우리의 이야기'
    UNION ALL SELECT '2026-10-02 18:00:00', '국악 퓨전 무대'
    UNION ALL SELECT '2027-02-12 19:00:00', '첫눈 콘서트'
    UNION ALL SELECT '2027-02-14 18:00:00', '크루 쇼케이스'
    UNION ALL SELECT '2027-02-16 19:00:00', '고요한 밤의 노래'
    UNION ALL SELECT '2027-02-17 19:00:00', '고요한 밤의 노래'
    UNION ALL SELECT '2027-02-18 18:00:00', '일렉트로닉 나이트'
    UNION ALL SELECT '2027-02-20 19:00:00', '뉴이어 카운트다운'
    UNION ALL SELECT '2027-02-22 18:00:00', '플로우 페스티벌'
    UNION ALL SELECT '2027-02-23 18:00:00', '플로우 페스티벌'
    UNION ALL SELECT '2027-07-16 19:00:00', '기억의 정원'
    UNION ALL SELECT '2026-10-10 18:00:00', '아카펠라 하모니'
    UNION ALL SELECT '2027-02-28 19:00:00', '팬미팅 우리의 계절'
    UNION ALL SELECT '2027-03-01 19:00:00', '팬미팅 우리의 계절'
    UNION ALL SELECT '2027-03-02 18:00:00', '레이트 나이트 잼'
    UNION ALL SELECT '2027-03-04 19:00:00', '손편지 콘서트'
    UNION ALL SELECT '2027-03-06 18:00:00', '오케스트라 신년 음악회'
    UNION ALL SELECT '2027-03-07 18:00:00', '오케스트라 신년 음악회'
    UNION ALL SELECT '2027-03-08 19:00:00', '글로우 업 투어'
    UNION ALL SELECT '2027-03-10 18:00:00', '붐뱁 리턴즈'
    UNION ALL SELECT '2027-08-01 19:00:00', '따뜻한 겨울'
    UNION ALL SELECT '2027-08-02 19:00:00', '따뜻한 겨울'
    UNION ALL SELECT '2026-10-18 18:00:00', '코미디 뮤직 쇼'
) AS v
JOIN performance AS p
    ON p.title = v.title
   AND p.performance_id > @before_max;


-- ============================================
-- 10-4. 스크롤용 SEAT
-- 새 공연의 회차마다 304석 (5번과 같은 배치)
-- ============================================

INSERT INTO seat
(section, row_num, seat_num, status, held_until, held_by, schedule_id)
WITH RECURSIVE n AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < 16
)
SELECT layout.section, CAST(r.i AS CHAR), c.i, 'AVAILABLE', NULL, NULL, s.schedule_id
FROM schedule AS s
CROSS JOIN (
    SELECT 'F1' AS section, 4 AS row_count, 8 AS seat_count
    UNION ALL SELECT 'F2', 4, 8
    UNION ALL SELECT 'F3', 4, 8
    UNION ALL SELECT 'F4', 4, 8
    UNION ALL SELECT 'S1', 4, 10
    UNION ALL SELECT 'S2', 4, 10
    UNION ALL SELECT 'G', 6, 16
) AS layout
JOIN n AS r ON r.i <= layout.row_count
JOIN n AS c ON c.i <= layout.seat_count
WHERE s.performance_id > @before_max;

-- ============================================
-- 11. demo.user 예매 내역 늘리기 (내 예매 내역 무한 스크롤용)
-- ============================================
-- 내 예매 내역 API는 reserved_at 내림차순, 같으면 reservation_id 내림차순으로 20건씩 준다.
-- 10번 공연 중 예매 오픈 전(UPCOMING)을 뺀 42개에 demo.user 예매를 1건씩 넣는다.
--   예매 중 공연: 확정, 다섯 건 중 한 건꼴로 본인 취소(수수료 10%, 좌석은 빈 좌석으로 돌아감)
--   종료 공연: 관람 완료
-- 앞 구역과 demo-reset.sql의 7건을 합치면 demo.user 예매는 49건(20 + 20 + 9, 3페이지)이다.
-- 2026-11-15 12:00:00에 예매한 건이 두 개라 보조 정렬이 맞는지도 확인할 수 있다.
-- 좌석은 공연마다 구역을 바꿔 1열 1번부터 1~2석이다. (인당 한도 2매 이하)
-- 같은 목록이 두 번 나오는데, 첫 번째는 예매를, 두 번째는 예매와 좌석의 연결을 만든다.
-- 임시 테이블은 RDS의 GTID 설정에 따라 막힐 수 있어서 쓰지 않았다.

INSERT INTO reservation
(status, reserved_at, canceled_at, member_id, schedule_id)
SELECT h.status, h.reserved_at, h.canceled_at, @demo_user, s.schedule_id
FROM (
    SELECT '별빛 쇼케이스' AS title, '2026-12-10 19:00:00' AS start_at, 'CONFIRMED' AS status, '2026-09-01 10:00:00' AS reserved_at, NULL AS canceled_at, 'F1' AS section, 1 AS seat_count
        UNION ALL SELECT '언더그라운드 사이퍼', '2026-12-12 18:00:00', 'CONFIRMED', '2026-09-03 02:00:00', NULL, 'F2', 2
        UNION ALL SELECT '겨울 편지', '2026-12-14 19:00:00', 'CANCELED', '2026-09-04 18:00:00', '2026-09-06 18:00:00', 'F3', 1
        UNION ALL SELECT '재즈 인 더 시티', '2026-12-16 18:00:00', 'CONFIRMED', '2026-09-06 10:00:00', NULL, 'F4', 2
        UNION ALL SELECT '루키 데뷔 콘서트', '2026-12-18 19:00:00', 'CONFIRMED', '2026-09-08 02:00:00', NULL, 'S1', 1
        UNION ALL SELECT '소울 앤 그루브', '2026-12-20 18:00:00', 'CONFIRMED', '2026-09-09 18:00:00', NULL, 'S2', 2
        UNION ALL SELECT '클래식 갈라', '2026-09-08 18:00:00', 'CONFIRMED', '2026-09-03 21:00:00', NULL, 'F1', 2
        UNION ALL SELECT '네온 시티 라이브', '2026-12-26 19:00:00', 'CONFIRMED', '2026-09-14 18:00:00', NULL, 'F2', 1
        UNION ALL SELECT '비트 메이커스 나이트', '2026-12-28 18:00:00', 'CONFIRMED', '2026-09-16 10:00:00', NULL, 'F3', 2
        UNION ALL SELECT '그리움의 온도', '2026-12-30 19:00:00', 'CONFIRMED', '2026-09-18 02:00:00', NULL, 'F4', 1
        UNION ALL SELECT '인디 밴드 페스티벌', '2027-01-01 18:00:00', 'CONFIRMED', '2026-09-19 18:00:00', NULL, 'S1', 2
        UNION ALL SELECT '아이돌 페스타', '2027-01-03 19:00:00', 'CANCELED', '2026-09-21 10:00:00', '2026-09-23 10:00:00', 'S2', 1
        UNION ALL SELECT '미드나잇 R&B', '2027-01-05 18:00:00', 'CONFIRMED', '2026-09-23 02:00:00', NULL, 'G', 2
        UNION ALL SELECT '어쿠스틱 피크닉', '2026-09-16 18:00:00', 'CONFIRMED', '2026-09-11 21:00:00', NULL, 'F2', 2
        UNION ALL SELECT '드림 스테이지', '2027-01-11 19:00:00', 'CONFIRMED', '2026-09-28 02:00:00', NULL, 'F3', 1
        UNION ALL SELECT '랩 배틀 파이널', '2027-01-13 18:00:00', 'CANCELED', '2026-09-29 18:00:00', '2026-10-01 18:00:00', 'F4', 2
        UNION ALL SELECT '별이 지는 밤', '2027-01-15 19:00:00', 'CONFIRMED', '2026-10-01 10:00:00', NULL, 'S1', 1
        UNION ALL SELECT '뮤지컬 넘버 콘서트', '2027-01-17 18:00:00', 'CONFIRMED', '2026-10-03 02:00:00', NULL, 'S2', 2
        UNION ALL SELECT '퍼플 나이트', '2027-01-19 19:00:00', 'CONFIRMED', '2026-10-04 18:00:00', NULL, 'G', 1
        UNION ALL SELECT '스트릿 사운드', '2027-01-21 18:00:00', 'CONFIRMED', '2026-10-06 10:00:00', NULL, 'F1', 2
        UNION ALL SELECT '록 스피릿', '2026-09-24 18:00:00', 'CONFIRMED', '2026-09-19 21:00:00', NULL, 'F3', 2
        UNION ALL SELECT '스타트라인 콘서트', '2027-01-27 19:00:00', 'CONFIRMED', '2026-10-11 10:00:00', NULL, 'F4', 1
        UNION ALL SELECT '그루브 시티', '2027-01-29 18:00:00', 'CONFIRMED', '2026-10-13 02:00:00', NULL, 'S1', 2
        UNION ALL SELECT '작은 위로', '2027-01-31 19:00:00', 'CONFIRMED', '2026-10-14 18:00:00', NULL, 'S2', 1
        UNION ALL SELECT '시네마 OST 콘서트', '2027-02-02 18:00:00', 'CANCELED', '2026-10-16 10:00:00', '2026-10-18 10:00:00', 'G', 2
        UNION ALL SELECT '하이라이트 리런', '2027-02-04 19:00:00', 'CONFIRMED', '2026-10-18 02:00:00', NULL, 'F1', 1
        UNION ALL SELECT '블루 노트 세션', '2027-02-06 18:00:00', 'CONFIRMED', '2026-10-19 18:00:00', NULL, 'F2', 2
        UNION ALL SELECT '국악 퓨전 무대', '2026-10-02 18:00:00', 'CONFIRMED', '2026-09-27 21:00:00', NULL, 'F4', 2
        UNION ALL SELECT '첫눈 콘서트', '2027-02-12 19:00:00', 'CANCELED', '2026-10-24 18:00:00', '2026-10-26 18:00:00', 'S1', 1
        UNION ALL SELECT '크루 쇼케이스', '2027-02-14 18:00:00', 'CONFIRMED', '2026-10-26 10:00:00', NULL, 'S2', 2
        UNION ALL SELECT '고요한 밤의 노래', '2027-02-16 19:00:00', 'CONFIRMED', '2026-10-28 02:00:00', NULL, 'G', 1
        UNION ALL SELECT '일렉트로닉 나이트', '2027-02-18 18:00:00', 'CONFIRMED', '2026-10-29 18:00:00', NULL, 'F1', 2
        UNION ALL SELECT '뉴이어 카운트다운', '2027-02-20 19:00:00', 'CONFIRMED', '2026-10-31 10:00:00', NULL, 'F2', 1
        UNION ALL SELECT '플로우 페스티벌', '2027-02-22 18:00:00', 'CANCELED', '2026-11-02 02:00:00', '2026-11-04 02:00:00', 'F3', 2
        UNION ALL SELECT '아카펠라 하모니', '2026-10-10 18:00:00', 'CONFIRMED', '2026-10-05 21:00:00', NULL, 'S1', 2
        UNION ALL SELECT '팬미팅 우리의 계절', '2027-02-28 19:00:00', 'CONFIRMED', '2026-11-15 12:00:00', NULL, 'S2', 1
        UNION ALL SELECT '레이트 나이트 잼', '2027-03-02 18:00:00', 'CONFIRMED', '2026-11-15 12:00:00', NULL, 'G', 2
        UNION ALL SELECT '손편지 콘서트', '2027-03-04 19:00:00', 'CANCELED', '2026-11-10 10:00:00', '2026-11-12 10:00:00', 'F1', 1
        UNION ALL SELECT '오케스트라 신년 음악회', '2027-03-06 18:00:00', 'CONFIRMED', '2026-11-12 02:00:00', NULL, 'F2', 2
        UNION ALL SELECT '글로우 업 투어', '2027-03-08 19:00:00', 'CONFIRMED', '2026-11-13 18:00:00', NULL, 'F3', 1
        UNION ALL SELECT '붐뱁 리턴즈', '2027-03-10 18:00:00', 'CONFIRMED', '2026-11-15 10:00:00', NULL, 'F4', 2
        UNION ALL SELECT '코미디 뮤직 쇼', '2026-10-18 18:00:00', 'CONFIRMED', '2026-10-13 21:00:00', NULL, 'S2', 2
) AS h
JOIN performance AS p ON p.title = h.title AND p.performance_id > @before_max
JOIN schedule AS s ON s.performance_id = p.performance_id AND s.start_at = h.start_at;

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, st.seat_id
FROM (
    SELECT '별빛 쇼케이스' AS title, '2026-12-10 19:00:00' AS start_at, 'CONFIRMED' AS status, '2026-09-01 10:00:00' AS reserved_at, NULL AS canceled_at, 'F1' AS section, 1 AS seat_count
        UNION ALL SELECT '언더그라운드 사이퍼', '2026-12-12 18:00:00', 'CONFIRMED', '2026-09-03 02:00:00', NULL, 'F2', 2
        UNION ALL SELECT '겨울 편지', '2026-12-14 19:00:00', 'CANCELED', '2026-09-04 18:00:00', '2026-09-06 18:00:00', 'F3', 1
        UNION ALL SELECT '재즈 인 더 시티', '2026-12-16 18:00:00', 'CONFIRMED', '2026-09-06 10:00:00', NULL, 'F4', 2
        UNION ALL SELECT '루키 데뷔 콘서트', '2026-12-18 19:00:00', 'CONFIRMED', '2026-09-08 02:00:00', NULL, 'S1', 1
        UNION ALL SELECT '소울 앤 그루브', '2026-12-20 18:00:00', 'CONFIRMED', '2026-09-09 18:00:00', NULL, 'S2', 2
        UNION ALL SELECT '클래식 갈라', '2026-09-08 18:00:00', 'CONFIRMED', '2026-09-03 21:00:00', NULL, 'F1', 2
        UNION ALL SELECT '네온 시티 라이브', '2026-12-26 19:00:00', 'CONFIRMED', '2026-09-14 18:00:00', NULL, 'F2', 1
        UNION ALL SELECT '비트 메이커스 나이트', '2026-12-28 18:00:00', 'CONFIRMED', '2026-09-16 10:00:00', NULL, 'F3', 2
        UNION ALL SELECT '그리움의 온도', '2026-12-30 19:00:00', 'CONFIRMED', '2026-09-18 02:00:00', NULL, 'F4', 1
        UNION ALL SELECT '인디 밴드 페스티벌', '2027-01-01 18:00:00', 'CONFIRMED', '2026-09-19 18:00:00', NULL, 'S1', 2
        UNION ALL SELECT '아이돌 페스타', '2027-01-03 19:00:00', 'CANCELED', '2026-09-21 10:00:00', '2026-09-23 10:00:00', 'S2', 1
        UNION ALL SELECT '미드나잇 R&B', '2027-01-05 18:00:00', 'CONFIRMED', '2026-09-23 02:00:00', NULL, 'G', 2
        UNION ALL SELECT '어쿠스틱 피크닉', '2026-09-16 18:00:00', 'CONFIRMED', '2026-09-11 21:00:00', NULL, 'F2', 2
        UNION ALL SELECT '드림 스테이지', '2027-01-11 19:00:00', 'CONFIRMED', '2026-09-28 02:00:00', NULL, 'F3', 1
        UNION ALL SELECT '랩 배틀 파이널', '2027-01-13 18:00:00', 'CANCELED', '2026-09-29 18:00:00', '2026-10-01 18:00:00', 'F4', 2
        UNION ALL SELECT '별이 지는 밤', '2027-01-15 19:00:00', 'CONFIRMED', '2026-10-01 10:00:00', NULL, 'S1', 1
        UNION ALL SELECT '뮤지컬 넘버 콘서트', '2027-01-17 18:00:00', 'CONFIRMED', '2026-10-03 02:00:00', NULL, 'S2', 2
        UNION ALL SELECT '퍼플 나이트', '2027-01-19 19:00:00', 'CONFIRMED', '2026-10-04 18:00:00', NULL, 'G', 1
        UNION ALL SELECT '스트릿 사운드', '2027-01-21 18:00:00', 'CONFIRMED', '2026-10-06 10:00:00', NULL, 'F1', 2
        UNION ALL SELECT '록 스피릿', '2026-09-24 18:00:00', 'CONFIRMED', '2026-09-19 21:00:00', NULL, 'F3', 2
        UNION ALL SELECT '스타트라인 콘서트', '2027-01-27 19:00:00', 'CONFIRMED', '2026-10-11 10:00:00', NULL, 'F4', 1
        UNION ALL SELECT '그루브 시티', '2027-01-29 18:00:00', 'CONFIRMED', '2026-10-13 02:00:00', NULL, 'S1', 2
        UNION ALL SELECT '작은 위로', '2027-01-31 19:00:00', 'CONFIRMED', '2026-10-14 18:00:00', NULL, 'S2', 1
        UNION ALL SELECT '시네마 OST 콘서트', '2027-02-02 18:00:00', 'CANCELED', '2026-10-16 10:00:00', '2026-10-18 10:00:00', 'G', 2
        UNION ALL SELECT '하이라이트 리런', '2027-02-04 19:00:00', 'CONFIRMED', '2026-10-18 02:00:00', NULL, 'F1', 1
        UNION ALL SELECT '블루 노트 세션', '2027-02-06 18:00:00', 'CONFIRMED', '2026-10-19 18:00:00', NULL, 'F2', 2
        UNION ALL SELECT '국악 퓨전 무대', '2026-10-02 18:00:00', 'CONFIRMED', '2026-09-27 21:00:00', NULL, 'F4', 2
        UNION ALL SELECT '첫눈 콘서트', '2027-02-12 19:00:00', 'CANCELED', '2026-10-24 18:00:00', '2026-10-26 18:00:00', 'S1', 1
        UNION ALL SELECT '크루 쇼케이스', '2027-02-14 18:00:00', 'CONFIRMED', '2026-10-26 10:00:00', NULL, 'S2', 2
        UNION ALL SELECT '고요한 밤의 노래', '2027-02-16 19:00:00', 'CONFIRMED', '2026-10-28 02:00:00', NULL, 'G', 1
        UNION ALL SELECT '일렉트로닉 나이트', '2027-02-18 18:00:00', 'CONFIRMED', '2026-10-29 18:00:00', NULL, 'F1', 2
        UNION ALL SELECT '뉴이어 카운트다운', '2027-02-20 19:00:00', 'CONFIRMED', '2026-10-31 10:00:00', NULL, 'F2', 1
        UNION ALL SELECT '플로우 페스티벌', '2027-02-22 18:00:00', 'CANCELED', '2026-11-02 02:00:00', '2026-11-04 02:00:00', 'F3', 2
        UNION ALL SELECT '아카펠라 하모니', '2026-10-10 18:00:00', 'CONFIRMED', '2026-10-05 21:00:00', NULL, 'S1', 2
        UNION ALL SELECT '팬미팅 우리의 계절', '2027-02-28 19:00:00', 'CONFIRMED', '2026-11-15 12:00:00', NULL, 'S2', 1
        UNION ALL SELECT '레이트 나이트 잼', '2027-03-02 18:00:00', 'CONFIRMED', '2026-11-15 12:00:00', NULL, 'G', 2
        UNION ALL SELECT '손편지 콘서트', '2027-03-04 19:00:00', 'CANCELED', '2026-11-10 10:00:00', '2026-11-12 10:00:00', 'F1', 1
        UNION ALL SELECT '오케스트라 신년 음악회', '2027-03-06 18:00:00', 'CONFIRMED', '2026-11-12 02:00:00', NULL, 'F2', 2
        UNION ALL SELECT '글로우 업 투어', '2027-03-08 19:00:00', 'CONFIRMED', '2026-11-13 18:00:00', NULL, 'F3', 1
        UNION ALL SELECT '붐뱁 리턴즈', '2027-03-10 18:00:00', 'CONFIRMED', '2026-11-15 10:00:00', NULL, 'F4', 2
        UNION ALL SELECT '코미디 뮤직 쇼', '2026-10-18 18:00:00', 'CONFIRMED', '2026-10-13 21:00:00', NULL, 'S2', 2
) AS h
JOIN performance AS p ON p.title = h.title AND p.performance_id > @before_max
JOIN schedule AS s ON s.performance_id = p.performance_id AND s.start_at = h.start_at
JOIN reservation AS r
    ON r.member_id = @demo_user
   AND r.schedule_id = s.schedule_id
   AND r.reserved_at = h.reserved_at
JOIN seat AS st
    ON st.schedule_id = s.schedule_id
   AND st.section = h.section
   AND st.row_num = '1'
   AND st.seat_num <= h.seat_count;

-- 확정 예매의 좌석만 예매 완료로 바꾼다 (취소 예매의 좌석은 빈 좌석)
UPDATE seat
SET status = 'RESERVED'
WHERE seat_id IN (
    SELECT rs.seat_id
    FROM reservation_seat AS rs
    JOIN reservation AS r ON r.reservation_id = rs.reservation_id
    JOIN schedule AS s ON s.schedule_id = r.schedule_id
    WHERE r.member_id = @demo_user
      AND r.status = 'CONFIRMED'
      AND s.performance_id > @before_max
);

INSERT INTO payment
(paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT
    SUM(pr.price),
    CASE WHEN r.status = 'CANCELED' THEN SUM(pr.price) DIV 10 ELSE 0 END,
    CASE WHEN r.status = 'CANCELED' THEN SUM(pr.price) - SUM(pr.price) DIV 10 ELSE 0 END,
    CASE WHEN r.status = 'CANCELED' THEN 'REFUNDED' ELSE 'PAID' END,
    r.reserved_at + INTERVAL 1 MINUTE,
    r.canceled_at,
    r.reservation_id
FROM reservation AS r
JOIN schedule AS s ON s.schedule_id = r.schedule_id
JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr ON pr.performance_id = s.performance_id AND pr.section = st.section
WHERE r.member_id = @demo_user
  AND s.performance_id > @before_max
GROUP BY r.reservation_id, r.status, r.reserved_at, r.canceled_at;
