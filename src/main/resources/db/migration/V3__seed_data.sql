
-- ============================================
-- V3__seed_data.sql
-- 이선좌 공연 예매 시스템 테스트 데이터
-- ============================================
-- V1: 테이블 생성
-- V2: CHECK 제약조건 추가
-- V3: 테스트 데이터 삽입
--
-- 주의: 빈 테이블을 기준으로 최초 1회 실행
-- running_time의 단위는 분(MINUTES)
-- 비밀번호는 로그인용 해시가 아닌 테스트 문자열


-- ============================================
-- 1. MEMBER
-- 회원 2명 (USER 1명, ADMIN 1명)
-- ============================================

INSERT INTO member
    (email, name, phone, role, created_at, password)
VALUES
    (
        'demo.user@example.com',
        '테스트회원',
        '01012345678',
        'USER',
        '2026-11-16 09:00:00',
        '$2a$10$.tD8wL83k7K3LGd0S/GQ7u8sYawsYeALHikOFn4RfdKrKdtgEy55u' -- Demo1234!
    );

SET @demo_member_id = LAST_INSERT_ID();

INSERT INTO member
    (email, name, phone, role, created_at, password)
VALUES
    (
        'demo.admin@example.com',
        '테스트관리자',
        '01087654321',
        'ADMIN',
        '2026-11-16 09:00:00',
        '$2a$10$.tD8wL83k7K3LGd0S/GQ7u8sYawsYeALHikOFn4RfdKrKdtgEy55u' -- Demo1234!
    );


-- ============================================
-- 2. PERFORMANCE
-- 공연 2개
-- ============================================

INSERT INTO performance
    (
        title,
        genre,
        running_time,
        description,
        status,
        venue_name,
        max_tickets_per_member
    )
VALUES
    (
        '이선좌 K-POP LIVE',
        'K_POP',
        120,
        'DB 실습용 K-POP 공연',
        'ON_SALE',
        '이선좌 공연장 A',
        4
    );

SET @performance1_id = LAST_INSERT_ID();

INSERT INTO performance
    (
        title,
        genre,
        running_time,
        description,
        status,
        venue_name,
        max_tickets_per_member
    )
VALUES
    (
        '이선좌 HIPHOP & R&B NIGHT',
        'HIPHOP_RANDB',
        90,
        'DB 실습용 힙합 공연',
        'UPCOMING',
        '이선좌 공연장 B',
        2
    );

SET @performance2_id = LAST_INSERT_ID();


-- ============================================
-- 3. PRICE
-- 공연마다 FS, VIP, R 가격 모두 등록
-- ============================================

INSERT INTO price
    (performance_id, seat_rank, price)
VALUES
    (@performance1_id, 'FS', 180000),
    (@performance1_id, 'VIP', 150000),
    (@performance1_id, 'R', 120000),

    (@performance2_id, 'FS', 160000),
    (@performance2_id, 'VIP', 130000),
    (@performance2_id, 'R', 100000);


-- ============================================
-- 4. SCHEDULE
-- 공연 회차 3개
-- ============================================

INSERT INTO schedule
    (start_at, status, performance_id)
VALUES
    (
        '2027-01-16 18:00:00',
        'OPEN',
        @performance1_id
    );

SET @schedule1_id = LAST_INSERT_ID();

INSERT INTO schedule
    (start_at, status, performance_id)
VALUES
    (
        '2027-01-17 18:00:00',
        'OPEN',
        @performance1_id
    );

SET @schedule2_id = LAST_INSERT_ID();

INSERT INTO schedule
    (start_at, status, performance_id)
VALUES
    (
        '2027-02-06 19:00:00',
        'OPEN',
        @performance2_id
    );

SET @schedule3_id = LAST_INSERT_ID();


-- ============================================
-- 5. SEAT
-- 회차당 좌석 4개, 총 12개
-- ============================================

-- 첫 번째 회차: FS 좌석 1개
INSERT INTO seat
    (
        section,
        row_num,
        seat_num,
        seat_rank,
        status,
        held_until,
        held_by,
        schedule_id
    )
VALUES
    (
        'F1',
        '1',
        1,
        'FS',
        'AVAILABLE',
        NULL,
        NULL,
        @schedule1_id
    );

SET @seat1_id = LAST_INSERT_ID();

-- 나머지 좌석 11개
INSERT INTO seat
    (
        section,
        row_num,
        seat_num,
        seat_rank,
        status,
        held_until,
        held_by,
        schedule_id
    )
VALUES
    -- 첫 번째 회차
    ('F2', '1', 2, 'FS', 'AVAILABLE', NULL, NULL, @schedule1_id),
    ('S1', '2', 1, 'VIP', 'AVAILABLE', NULL, NULL, @schedule1_id),
    ('G', '3', 1, 'R', 'AVAILABLE', NULL, NULL, @schedule1_id),

    -- 두 번째 회차
    ('F3', '1', 1, 'FS', 'AVAILABLE', NULL, NULL, @schedule2_id),
    ('S2', '2', 1, 'VIP', 'AVAILABLE', NULL, NULL, @schedule2_id),
    ('G', '3', 2, 'R', 'AVAILABLE', NULL, NULL, @schedule2_id),
    ('F4', '1', 2, 'FS', 'AVAILABLE', NULL, NULL, @schedule2_id),

    -- 세 번째 회차
    ('F1', '1', 1, 'FS', 'AVAILABLE', NULL, NULL, @schedule3_id),
    ('S1', '2', 1, 'VIP', 'AVAILABLE', NULL, NULL, @schedule3_id),
    ('G', '3', 1, 'R', 'AVAILABLE', NULL, NULL, @schedule3_id),
    ('F2', '1', 2, 'FS', 'AVAILABLE', NULL, NULL, @schedule3_id);


-- 세 번째 회차에서 예매할 VIP 좌석 조회
SELECT seat_id INTO @seat2_id
FROM seat
WHERE schedule_id = @schedule3_id
  AND section = 'S1'
  AND row_num = '2'
  AND seat_num = 1;


-- ============================================
-- 6. RESERVATION
-- 확정된 예매 2건
-- ============================================

INSERT INTO reservation
    (
        status,
        reserved_at,
        canceled_at,
        member_id,
        schedule_id,
        seat_id
    )
VALUES
    (
        'CONFIRMED',
        '2026-12-20 10:00:00',
        NULL,
        @demo_member_id,
        @schedule1_id,
        @seat1_id
    );

SET @reservation1_id = LAST_INSERT_ID();

INSERT INTO reservation
    (
        status,
        reserved_at,
        canceled_at,
        member_id,
        schedule_id,
        seat_id
    )
VALUES
    (
        'CONFIRMED',
        '2027-01-10 10:00:00',
        NULL,
        @demo_member_id,
        @schedule3_id,
        @seat2_id
    );

SET @reservation2_id = LAST_INSERT_ID();


-- 예매 확정된 좌석 상태 변경
UPDATE seat
SET status = 'RESERVED'
WHERE seat_id IN (@seat1_id, @seat2_id);


-- ============================================
-- 7. PAYMENT
-- 결제 2건
-- ============================================

INSERT INTO payment
    (
        paid_amount,
        cancel_fee,
        refund_amount,
        status,
        paid_at,
        refunded_at,
        reservation_id
    )
VALUES
    (
        180000,
        0,
        0,
        'PAID',
        '2026-12-20 10:01:00',
        NULL,
        @reservation1_id
    ),
    (
        130000,
        0,
        0,
        'PAID',
        '2027-01-10 10:01:00',
        NULL,
        @reservation2_id
    );
