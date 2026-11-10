-- ============================================
-- V2__seed_data.sql
-- 공연 예매 시스템 초기 샘플 데이터
-- MySQL 8.4
-- ============================================

-- 1. 회원 3명
-- 개발용 공통 비밀번호: Demo1234!
-- BCrypt로 해싱된 테스트 비밀번호
-- 실제 사용자 계정에 사용하지 말 것

INSERT INTO member
(member_id, email, password, name, phone, role, created_at)
VALUES
(1, 'user1@example.com',
 '$2y$10$QjIM5L2JtkvY6.FBR3ImnulkcKNEvRA37JkmrN1aZGJ6B8lKLAJHi',
 '김민지', '010-1111-1111', 'USER', NOW()),

(2, 'user2@example.com',
 '$2y$10$QjIM5L2JtkvY6.FBR3ImnulkcKNEvRA37JkmrN1aZGJ6B8lKLAJHi',
 '이서준', '010-2222-2222', 'USER', NOW()),

(3, 'admin@example.com',
 '$2y$10$QjIM5L2JtkvY6.FBR3ImnulkcKNEvRA37JkmrN1aZGJ6B8lKLAJHi',
 '관리자', '010-3333-3333', 'ADMIN', NOW());


-- 2. 공연장 1개

INSERT INTO venue
(venue_id, name, address)
VALUES
(1, '이선좌 아트홀', '서울특별시 동작구 상도로 100');


-- 3. 좌석 등급

INSERT INTO seat_rank
(seat_rank_id, grade, sort_order)
VALUES
(1, 'VIP', 1),
(2, 'R', 2),
(3, 'S', 3);


-- 4. 공연장 좌석 100석
-- A구역 1~10열, 각 열 1~10번
-- 총 10 x 10 = 100석

INSERT INTO seat
(venue_id, section, row_num, seat_num)
SELECT
    1,
    'A',
    CAST(r.num AS CHAR),
    s.num
FROM
(
    SELECT 1 AS num UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5 UNION ALL
    SELECT 6 UNION ALL
    SELECT 7 UNION ALL
    SELECT 8 UNION ALL
    SELECT 9 UNION ALL
    SELECT 10
) r
CROSS JOIN
(
    SELECT 1 AS num UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5 UNION ALL
    SELECT 6 UNION ALL
    SELECT 7 UNION ALL
    SELECT 8 UNION ALL
    SELECT 9 UNION ALL
    SELECT 10
) s;


-- 5. 공연 2개
-- running_time 단위: 초

INSERT INTO performance
(performance_id, title, genre, running_time,
 description, status, venue_id)
VALUES
(
    1,
    '별빛 아래의 약속',
    'MUSICAL',
    9000,
    '별빛 아래에서 펼쳐지는 감동적인 창작 뮤지컬',
    'ON_SALE',
    1
),
(
    2,
    '2026 WINTER LIVE',
    'CONCERT',
    7200,
    '겨울을 맞아 개최되는 특별 라이브 콘서트',
    'ON_SALE',
    1
);


-- 6. 공연별 좌석 가격
-- PRICE는 performance_id + seat_rank_id 복합 PK

INSERT INTO price
(performance_id, seat_rank_id, price)
VALUES
(1, 1, 150000),
(1, 2, 120000),
(1, 3, 90000),

(2, 1, 180000),
(2, 2, 140000),
(2, 3, 110000);


-- 7. 공연 회차
-- 공연당 2회차, 총 4회차

INSERT INTO schedule
(schedule_id, performance_id, start_at, status)
VALUES
(1, 1, '2026-12-12 14:00:00', 'OPEN'),
(2, 1, '2026-12-13 18:00:00', 'OPEN'),

(3, 2, '2026-12-19 18:00:00', 'OPEN'),
(4, 2, '2026-12-20 18:00:00', 'OPEN');


-- 8. 공연별 좌석 생성
-- 두 공연 모두 100석 사용
--
-- 1~3열: VIP
-- 4~7열: R
-- 8~10열: S
--
-- 총 200개 performance_seat 생성

INSERT INTO performance_seat
(performance_id, seat_id, seat_rank_id)
SELECT
    p.performance_id,
    s.seat_id,
    CASE
        WHEN CAST(s.row_num AS UNSIGNED) <= 3 THEN 1
        WHEN CAST(s.row_num AS UNSIGNED) <= 7 THEN 2
        ELSE 3
    END
FROM performance p
JOIN seat s
    ON p.venue_id = s.venue_id
WHERE p.performance_id IN (1, 2);


-- 9. 회차별 좌석 생성
-- 4회차 x 100석 = 총 400석
-- 초기 상태는 모두 AVAILABLE

INSERT INTO scheduled_seat
(schedule_id, performance_seat_id, status,
 held_by, held_until)
SELECT
    sc.schedule_id,
    ps.performance_seat_id,
    'AVAILABLE',
    NULL,
    NULL
FROM schedule sc
JOIN performance_seat ps
    ON sc.performance_id = ps.performance_id
WHERE sc.schedule_id IN (1, 2, 3, 4);


-- ============================================
-- 샘플 데이터 삽입 완료
-- ============================================