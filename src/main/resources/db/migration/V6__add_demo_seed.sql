-- ============================================
-- V6__add_demo_seed.sql
-- 시연, 관리자 기능, 동시성 테스트에 쓰는 기본 데이터
-- ============================================
-- 여기에는 시간이 지나도 바뀌지 않는 데이터만 둔다.
-- 예매 중인 공연(K-POP LIVE, HIPHOP & R&B NIGHT)의 예매와 선점 상태는
-- 시각에 따라 달라지므로 scripts/demo-reset.sql이 만든다.
--
-- 테스트 계정 비밀번호: 모두 Demo1234! (V3와 같은 BCrypt 해시)
--
-- 좌석 배치: 관리자 공연 등록(ScheduleSeatRepository)과 같게 회차마다
--   7구역(F1~F4, S1, S2, G) x 15열 x 10석 = 1,050석
--
-- 결제 상태와 좌석 (DFD 10.0 예매 취소, 11.0 환불 처리 기준)
--   PAID      예매 확정. 좌석 RESERVED
--   CANCELED  사용자가 취소해 환불 진행 중. 수수료와 환불 예정액이 기록되고 좌석은 바로 AVAILABLE
--   REFUNDED  관리자가 환불 완료. refunded_at이 기록된다. 좌석은 취소할 때 이미 반환됐다
--   예매(reservation)는 PAID면 CONFIRMED, 나머지는 CANCELED
-- 취소 수수료: 공연 날짜까지 남은 일수 7일 이상 0%, 3~6일 10%, 1~2일 30% (주최 측 공연 취소는 0%)
--
-- 이 파일이 만드는 것
--   1~5. 회원 606명, 공연 52개(상태별 4개 + 목록 스크롤용 48개), 가격, 회차, 좌석
--   6. 매진 공연: 1,050석 전부 예매
--   7. 종료 공연: 60석 예매
--   8. 주최 측이 취소한 공연: 환불 진행 중 9건, 환불 완료 6건 (관리자 일괄 환불 테스트용)
--   9. demo.user 예매 내역 42건 (내 예매 내역 무한 스크롤용)
--   10. 좌석 상태 맞추기
--   11. 포스터 키 (이미지는 S3 버킷의 seed/ 아래, 원본은 scripts/seed-posters/)


-- ============================================
-- 1. MEMBER
-- 한도 테스트 회원 1명, 팀원 수동 테스트 계정 5명, 부하 테스트 회원 600명
-- 부하 테스트 회원은 k6(load001~load100)와 예매 기록을 채우는 데 쓴다
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

-- load001@example.com ~ load600@example.com
INSERT INTO member
(email, name, phone, role, created_at, password)
WITH RECURSIVE n AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < 600
)
SELECT
    CONCAT('load', LPAD(i, 3, '0'), '@example.com'),
    CONCAT('부하테스트', LPAD(i, 3, '0')),
    NULL,
    'USER',
    '2026-11-16 09:00:00',
    @demo_password
FROM n;

SELECT member_id INTO @demo_user FROM member WHERE email = 'demo.user@example.com';


-- ============================================
-- 2. PERFORMANCE
-- 기존 공연 2개는 ON_SALE이라 나머지 상태를 하나씩 추가하고,
-- 공연 목록과 관리자 공연 목록 스크롤용 공연 48개를 더한다
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

-- 스크롤용 공연: 장르 4개를 번갈아 넣고, 상태는 ON_SALE 36개, UPCOMING 6개, CLOSED 6개

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

SELECT MIN(performance_id) INTO @scroll_first_id
FROM performance
WHERE performance_id > @canceled_id;


-- ============================================
-- 3. PRICE
-- ============================================

-- 상태별 공연 4개
INSERT INTO price (performance_id, section, price)
SELECT p.performance_id, s.section, s.price
FROM performance AS p
CROSS JOIN (
    SELECT 'F1' AS section, 170000 AS price
    UNION ALL SELECT 'F2', 170000
    UNION ALL SELECT 'F3', 170000
    UNION ALL SELECT 'F4', 170000
    UNION ALL SELECT 'S1', 140000
    UNION ALL SELECT 'S2', 140000
    UNION ALL SELECT 'G', 110000
) AS s
WHERE p.performance_id IN (@upcoming_id, @sold_out_id, @closed_id, @canceled_id);

-- 스크롤용 공연: 장르별로 가격대를 다르게 둔다
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
WHERE p.performance_id >= @scroll_first_id;


-- ============================================
-- 4. SCHEDULE
-- 회차 날짜를 공연마다 다르게 잡아 같은 공연장에서 시간이 겹치는 회차가 없다
-- ============================================

SELECT performance_id INTO @kpop_id FROM performance WHERE title = '이선좌 K-POP LIVE';

INSERT INTO schedule (start_at, performance_id)
VALUES
    ('2026-12-03 19:00:00', @kpop_id),        -- 발표 당일 회차 (당일 취소 불가 시연용)
    ('2027-03-06 18:00:00', @upcoming_id),
    ('2027-03-07 18:00:00', @upcoming_id),
    ('2027-01-30 18:00:00', @sold_out_id),
    ('2026-10-18 17:00:00', @closed_id),
    ('2027-03-20 19:00:00', @canceled_id);

SELECT schedule_id INTO @sold_out_schedule_id FROM schedule WHERE performance_id = @sold_out_id;
SELECT schedule_id INTO @closed_schedule_id FROM schedule WHERE performance_id = @closed_id;
SELECT schedule_id INTO @canceled_schedule_id FROM schedule WHERE performance_id = @canceled_id;

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
   AND p.performance_id >= @scroll_first_id;


-- ============================================
-- 5. SEAT
-- 모든 회차에 1,050석. V3에서 이미 만든 좌석(F1 1열 1번 등)은 건너뛴다
-- ============================================

INSERT INTO seat
(section, row_num, seat_num, status, held_until, held_by, schedule_id)
WITH RECURSIVE n AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < 15
)
SELECT sec.section, CAST(r.i AS CHAR), c.i, 'AVAILABLE', NULL, NULL, s.schedule_id
FROM schedule AS s
CROSS JOIN (
    SELECT 'F1' AS section
    UNION ALL SELECT 'F2'
    UNION ALL SELECT 'F3'
    UNION ALL SELECT 'F4'
    UNION ALL SELECT 'S1'
    UNION ALL SELECT 'S2'
    UNION ALL SELECT 'G'
) AS sec
JOIN n AS r ON r.i <= 15
JOIN n AS c ON c.i <= 10
WHERE NOT EXISTS (
    SELECT 1
    FROM seat AS existing
    WHERE existing.schedule_id = s.schedule_id
      AND existing.section = sec.section
      AND existing.row_num = CAST(r.i AS CHAR)
      AND existing.seat_num = c.i
);


-- ============================================
-- 6. 매진 공연: 1,050석을 4석씩 묶어 263건 예매 (인당 한도 4)
-- 첫 묶음은 demo.user, 나머지는 load001~load262
-- 예매 시각을 2시간 간격으로 흩어 관리자 통계에 쓰고, 예매와 좌석을 잇는 키로도 쓴다
-- ============================================

SET @sold_out_base = TIMESTAMP('2026-11-01 10:00:00');

INSERT INTO reservation
(status, reserved_at, canceled_at, member_id, schedule_id)
SELECT
    'CONFIRMED',
    @sold_out_base + INTERVAL g.grp * 2 HOUR,
    NULL,
    COALESCE(IF(g.grp = 0, @demo_user, NULL), m.member_id),
    @sold_out_schedule_id
FROM (
    SELECT DISTINCT (ROW_NUMBER() OVER (ORDER BY section, CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 4 AS grp
    FROM seat
    WHERE schedule_id = @sold_out_schedule_id
) AS g
LEFT JOIN member AS m
    ON m.email = CONCAT('load', LPAD(g.grp, 3, '0'), '@example.com');

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, t.seat_id
FROM (
    SELECT seat_id, (ROW_NUMBER() OVER (ORDER BY section, CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 4 AS grp
    FROM seat
    WHERE schedule_id = @sold_out_schedule_id
) AS t
JOIN reservation AS r
    ON r.schedule_id = @sold_out_schedule_id
   AND r.reserved_at = @sold_out_base + INTERVAL t.grp * 2 HOUR;


-- ============================================
-- 7. 종료 공연: G구역 앞 60석을 2석씩 묶어 30건 예매
-- 첫 묶음은 demo.user(관람 완료), 나머지는 load001~load029
-- ============================================

SET @closed_base = TIMESTAMP('2026-09-20 10:00:00');

INSERT INTO reservation
(status, reserved_at, canceled_at, member_id, schedule_id)
SELECT
    'CONFIRMED',
    @closed_base + INTERVAL g.grp * 12 HOUR,
    NULL,
    COALESCE(IF(g.grp = 0, @demo_user, NULL), m.member_id),
    @closed_schedule_id
FROM (
    SELECT DISTINCT grp
    FROM (
        SELECT (ROW_NUMBER() OVER (ORDER BY CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 2 AS grp
        FROM seat
        WHERE schedule_id = @closed_schedule_id AND section = 'G'
    ) AS numbered
    WHERE grp < 30
) AS g
LEFT JOIN member AS m
    ON m.email = CONCAT('load', LPAD(g.grp, 3, '0'), '@example.com');

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, t.seat_id
FROM (
    SELECT seat_id, (ROW_NUMBER() OVER (ORDER BY CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 2 AS grp
    FROM seat
    WHERE schedule_id = @closed_schedule_id AND section = 'G'
) AS t
JOIN reservation AS r
    ON r.schedule_id = @closed_schedule_id
   AND r.reserved_at = @closed_base + INTERVAL t.grp * 12 HOUR
WHERE t.grp < 30;

-- 6, 7번 예매의 결제 (모두 PAID)
INSERT INTO payment
(paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT SUM(pr.price), 0, 0, 'PAID', r.reserved_at + INTERVAL 1 MINUTE, NULL, r.reservation_id
FROM reservation AS r
JOIN schedule AS sc ON sc.schedule_id = r.schedule_id
JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr ON pr.performance_id = sc.performance_id AND pr.section = st.section
WHERE r.schedule_id IN (@sold_out_schedule_id, @closed_schedule_id)
GROUP BY r.reservation_id, r.reserved_at;


-- ============================================
-- 8. 주최 측이 취소한 공연: S1구역 앞 30석을 2석씩 묶어 15건
--   묶음 0: demo.user, 환불 완료
--   묶음 1~5: load001~load005, 환불 완료
--   묶음 6~14: load006~load014, 환불 진행 중 (관리자가 일괄 환불할 대상)
-- 공연 취소라 수수료는 0원, 예매는 모두 CANCELED
-- ============================================

SET @canceled_base = TIMESTAMP('2026-11-02 20:00:00');
SET @organizer_canceled_at = TIMESTAMP('2026-11-18 10:00:00');

INSERT INTO reservation
(status, reserved_at, canceled_at, member_id, schedule_id)
SELECT
    'CANCELED',
    @canceled_base + INTERVAL g.grp * 9 HOUR,
    @organizer_canceled_at,
    COALESCE(IF(g.grp = 0, @demo_user, NULL), m.member_id),
    @canceled_schedule_id
FROM (
    SELECT DISTINCT grp
    FROM (
        SELECT (ROW_NUMBER() OVER (ORDER BY CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 2 AS grp
        FROM seat
        WHERE schedule_id = @canceled_schedule_id AND section = 'S1'
    ) AS numbered
    WHERE grp < 15
) AS g
LEFT JOIN member AS m
    ON m.email = CONCAT('load', LPAD(g.grp, 3, '0'), '@example.com');

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, t.seat_id
FROM (
    SELECT seat_id, (ROW_NUMBER() OVER (ORDER BY CAST(row_num AS UNSIGNED), seat_num) - 1) DIV 2 AS grp
    FROM seat
    WHERE schedule_id = @canceled_schedule_id AND section = 'S1'
) AS t
JOIN reservation AS r
    ON r.schedule_id = @canceled_schedule_id
   AND r.reserved_at = @canceled_base + INTERVAL t.grp * 9 HOUR
WHERE t.grp < 15;

INSERT INTO payment
(paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
SELECT
    SUM(pr.price),
    0,
    SUM(pr.price),
    IF(r.reserved_at < @canceled_base + INTERVAL 6 * 9 HOUR, 'REFUNDED', 'CANCELED'),
    r.reserved_at + INTERVAL 1 MINUTE,
    IF(r.reserved_at < @canceled_base + INTERVAL 6 * 9 HOUR, @organizer_canceled_at + INTERVAL 1 DAY, NULL),
    r.reservation_id
FROM reservation AS r
JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
JOIN seat AS st ON st.seat_id = rs.seat_id
JOIN price AS pr ON pr.performance_id = @canceled_id AND pr.section = st.section
WHERE r.schedule_id = @canceled_schedule_id
GROUP BY r.reservation_id, r.reserved_at;


-- ============================================
-- 9. demo.user 예매 내역 (내 예매 내역 무한 스크롤용)
-- 스크롤용 공연 중 예매 오픈 전(UPCOMING)을 뺀 42개에 1건씩
--   예매 중 공연: 확정, 환불 진행 중, 환불 완료 (공연까지 7일 넘게 남았을 때 취소해서 수수료 0%)
--   종료 공연: 관람 완료, 공연 5일 전 취소(수수료 10%), 2일 전 취소(수수료 30%)
-- 6~8번, demo-reset.sql과 합치면 demo.user 예매는 49건(20 + 20 + 9, 3페이지)
-- 2026-11-15 12:00:00에 예매한 건이 두 개라 보조 정렬(reservation_id)도 확인할 수 있다
-- 좌석은 공연마다 구역을 바꿔 1열 1번부터 1~2석이다
-- 목록을 여러 번 쓰므로 작업용 테이블에 넣었다가 끝나면 지운다
-- ============================================

DROP TABLE IF EXISTS seed_demo_history;

CREATE TABLE seed_demo_history (
    title VARCHAR(100) NOT NULL,
    start_at DATETIME NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    reserved_at DATETIME NOT NULL,
    canceled_at DATETIME NULL,
    refunded_at DATETIME NULL,
    section VARCHAR(2) NOT NULL,
    seat_count INT NOT NULL
);

INSERT INTO seed_demo_history
(title, start_at, payment_status, reserved_at, canceled_at, refunded_at, section, seat_count)
VALUES
    ('별빛 쇼케이스', '2026-12-10 19:00:00', 'PAID', '2026-09-01 10:00:00', NULL, NULL, 'F1', 1),
    ('언더그라운드 사이퍼', '2026-12-12 18:00:00', 'PAID', '2026-09-03 13:00:00', NULL, NULL, 'F2', 2),
    ('겨울 편지', '2026-12-14 19:00:00', 'CANCELED', '2026-09-04 15:00:00', '2026-09-06 15:00:00', NULL, 'F3', 1),
    ('재즈 인 더 시티', '2026-12-16 18:00:00', 'PAID', '2026-09-06 19:00:00', NULL, NULL, 'F4', 2),
    ('루키 데뷔 콘서트', '2026-12-18 19:00:00', 'REFUNDED', '2026-09-08 21:00:00', '2026-09-10 21:00:00', '2026-09-11 21:00:00', 'S1', 1),
    ('소울 앤 그루브', '2026-12-20 18:00:00', 'PAID', '2026-09-09 10:00:00', NULL, NULL, 'S2', 2),
    ('클래식 갈라', '2026-09-08 18:00:00', 'PAID', '2026-08-19 20:00:00', NULL, NULL, 'F1', 2),
    ('네온 시티 라이브', '2026-12-26 19:00:00', 'CANCELED', '2026-09-14 19:00:00', '2026-09-16 19:00:00', NULL, 'F2', 1),
    ('비트 메이커스 나이트', '2026-12-28 18:00:00', 'PAID', '2026-09-16 21:00:00', NULL, NULL, 'F3', 2),
    ('그리움의 온도', '2026-12-30 19:00:00', 'REFUNDED', '2026-09-18 10:00:00', '2026-09-20 10:00:00', '2026-09-21 10:00:00', 'F4', 1),
    ('인디 밴드 페스티벌', '2027-01-01 18:00:00', 'PAID', '2026-09-19 13:00:00', NULL, NULL, 'S1', 2),
    ('아이돌 페스타', '2027-01-03 19:00:00', 'PAID', '2026-09-21 15:00:00', NULL, NULL, 'S2', 1),
    ('미드나잇 R&B', '2027-01-05 18:00:00', 'PAID', '2026-09-23 19:00:00', NULL, NULL, 'G', 2),
    ('어쿠스틱 피크닉', '2026-09-16 18:00:00', 'REFUNDED', '2026-08-27 20:00:00', '2026-09-11 11:00:00', '2026-09-12 11:00:00', 'F2', 2),
    ('드림 스테이지', '2027-01-11 19:00:00', 'REFUNDED', '2026-09-28 13:00:00', '2026-09-30 13:00:00', '2026-10-01 13:00:00', 'F3', 1),
    ('랩 배틀 파이널', '2027-01-13 18:00:00', 'PAID', '2026-09-29 15:00:00', NULL, NULL, 'F4', 2),
    ('별이 지는 밤', '2027-01-15 19:00:00', 'PAID', '2026-10-01 19:00:00', NULL, NULL, 'S1', 1),
    ('뮤지컬 넘버 콘서트', '2027-01-17 18:00:00', 'PAID', '2026-10-03 21:00:00', NULL, NULL, 'S2', 2),
    ('퍼플 나이트', '2027-01-19 19:00:00', 'CANCELED', '2026-10-04 10:00:00', '2026-10-06 10:00:00', NULL, 'G', 1),
    ('스트릿 사운드', '2027-01-21 18:00:00', 'PAID', '2026-10-06 13:00:00', NULL, NULL, 'F1', 2),
    ('록 스피릿', '2026-09-24 18:00:00', 'REFUNDED', '2026-09-04 20:00:00', '2026-09-22 11:00:00', '2026-09-23 11:00:00', 'F3', 2),
    ('스타트라인 콘서트', '2027-01-27 19:00:00', 'PAID', '2026-10-11 21:00:00', NULL, NULL, 'F4', 1),
    ('그루브 시티', '2027-01-29 18:00:00', 'PAID', '2026-10-13 10:00:00', NULL, NULL, 'S1', 2),
    ('작은 위로', '2027-01-31 19:00:00', 'CANCELED', '2026-10-14 13:00:00', '2026-10-16 13:00:00', NULL, 'S2', 1),
    ('시네마 OST 콘서트', '2027-02-02 18:00:00', 'PAID', '2026-10-16 15:00:00', NULL, NULL, 'G', 2),
    ('하이라이트 리런', '2027-02-04 19:00:00', 'REFUNDED', '2026-10-18 19:00:00', '2026-10-20 19:00:00', '2026-10-21 19:00:00', 'F1', 1),
    ('블루 노트 세션', '2027-02-06 18:00:00', 'PAID', '2026-10-19 21:00:00', NULL, NULL, 'F2', 2),
    ('국악 퓨전 무대', '2026-10-02 18:00:00', 'PAID', '2026-09-12 20:00:00', NULL, NULL, 'F4', 2),
    ('첫눈 콘서트', '2027-02-12 19:00:00', 'CANCELED', '2026-10-24 15:00:00', '2026-10-26 15:00:00', NULL, 'S1', 1),
    ('크루 쇼케이스', '2027-02-14 18:00:00', 'PAID', '2026-10-26 19:00:00', NULL, NULL, 'S2', 2),
    ('고요한 밤의 노래', '2027-02-16 19:00:00', 'REFUNDED', '2026-10-28 21:00:00', '2026-10-30 21:00:00', '2026-10-31 21:00:00', 'G', 1),
    ('일렉트로닉 나이트', '2027-02-18 18:00:00', 'PAID', '2026-10-29 10:00:00', NULL, NULL, 'F1', 2),
    ('뉴이어 카운트다운', '2027-02-20 19:00:00', 'PAID', '2026-10-31 13:00:00', NULL, NULL, 'F2', 1),
    ('플로우 페스티벌', '2027-02-22 18:00:00', 'PAID', '2026-11-02 15:00:00', NULL, NULL, 'F3', 2),
    ('아카펠라 하모니', '2026-10-10 18:00:00', 'REFUNDED', '2026-09-20 20:00:00', '2026-10-05 11:00:00', '2026-10-06 11:00:00', 'S1', 2),
    ('팬미팅 우리의 계절', '2027-02-28 19:00:00', 'REFUNDED', '2026-11-15 12:00:00', '2026-11-17 12:00:00', '2026-11-18 12:00:00', 'S2', 1),
    ('레이트 나이트 잼', '2027-03-02 18:00:00', 'PAID', '2026-11-15 12:00:00', NULL, NULL, 'G', 2),
    ('손편지 콘서트', '2027-03-04 19:00:00', 'PAID', '2026-11-10 15:00:00', NULL, NULL, 'F1', 1),
    ('오케스트라 신년 음악회', '2027-03-06 18:00:00', 'PAID', '2026-11-12 19:00:00', NULL, NULL, 'F2', 2),
    ('글로우 업 투어', '2027-03-08 19:00:00', 'CANCELED', '2026-11-13 21:00:00', '2026-11-15 21:00:00', NULL, 'F3', 1),
    ('붐뱁 리턴즈', '2027-03-10 18:00:00', 'PAID', '2026-11-15 10:00:00', NULL, NULL, 'F4', 2),
    ('코미디 뮤직 쇼', '2026-10-18 18:00:00', 'REFUNDED', '2026-09-28 20:00:00', '2026-10-16 11:00:00', '2026-10-17 11:00:00', 'S2', 2);

INSERT INTO reservation
(status, reserved_at, canceled_at, member_id, schedule_id)
SELECT IF(h.payment_status = 'PAID', 'CONFIRMED', 'CANCELED'), h.reserved_at, h.canceled_at, @demo_user, s.schedule_id
FROM seed_demo_history AS h
JOIN performance AS p ON p.title = h.title AND p.performance_id >= @scroll_first_id
JOIN schedule AS s ON s.performance_id = p.performance_id AND s.start_at = h.start_at;

INSERT INTO reservation_seat (reservation_id, seat_id)
SELECT r.reservation_id, st.seat_id
FROM seed_demo_history AS h
JOIN performance AS p ON p.title = h.title AND p.performance_id >= @scroll_first_id
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

-- 수수료율: 취소일부터 공연 날짜까지 남은 일수로 정한다 (#27 CancelFeePolicy와 같은 규칙)
INSERT INTO payment
(paid_amount, cancel_fee, refund_amount, status, paid_at, refunded_at, reservation_id)
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
        h.payment_status,
        h.reserved_at,
        h.refunded_at,
        SUM(pr.price) AS paid_amount,
        CASE
            WHEN h.payment_status = 'PAID' THEN 0
            WHEN DATEDIFF(DATE(h.start_at), DATE(h.canceled_at)) >= 7 THEN 0
            WHEN DATEDIFF(DATE(h.start_at), DATE(h.canceled_at)) >= 3 THEN SUM(pr.price) * 10 DIV 100
            ELSE SUM(pr.price) * 30 DIV 100
        END AS cancel_fee
    FROM seed_demo_history AS h
    JOIN performance AS p ON p.title = h.title AND p.performance_id >= @scroll_first_id
    JOIN schedule AS s ON s.performance_id = p.performance_id AND s.start_at = h.start_at
    JOIN reservation AS r
        ON r.member_id = @demo_user
       AND r.schedule_id = s.schedule_id
       AND r.reserved_at = h.reserved_at
    JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
    JOIN seat AS st ON st.seat_id = rs.seat_id
    JOIN price AS pr ON pr.performance_id = p.performance_id AND pr.section = st.section
    GROUP BY r.reservation_id, h.payment_status, h.reserved_at, h.refunded_at, h.start_at, h.canceled_at
) AS t;

DROP TABLE seed_demo_history;


-- ============================================
-- 10. 좌석 상태 맞추기
-- 결제가 PAID(확정)인 예매의 좌석만 RESERVED. 취소된 예매의 좌석은 취소 시점에 반환됐다
-- ============================================

UPDATE seat
SET status = 'RESERVED'
WHERE seat_id IN (
    SELECT rs.seat_id
    FROM reservation_seat AS rs
    JOIN payment AS pay ON pay.reservation_id = rs.reservation_id
    WHERE pay.status = 'PAID'
);


-- ============================================
-- 11. 포스터
-- poster_key는 URL이 아니라 S3 객체 키다. 앱(#36 PosterStorage)이 이 키로 서명 URL을 만든다.
-- 이미지는 S3 버킷의 seed/ 아래에 수동으로 올려 두었다(seed/01.jpg ~ seed/54.jpg 중 52장).
-- 원본 파일은 scripts/seed-posters/에 있다. 버킷을 새로 만들면 이 파일들을 seed/에 다시 올린다.
-- 관리자가 등록하는 포스터는 posters/ 아래에 들어가므로(#36) 시드 이미지와 섞이지 않는다.
-- 기본 이미지 확인용으로 '이선좌 겨울 힙합 쇼', '코미디 뮤직 쇼'는 포스터를 비워 둔다.
-- 이미 포스터가 있는 공연(관리자가 등록한 것 등)은 건드리지 않는다.
-- ============================================

UPDATE performance AS p
JOIN (
    SELECT '이선좌 K-POP LIVE' AS title, 'seed/01.jpg' AS poster_key
    UNION ALL SELECT '이선좌 HIPHOP & R&B NIGHT', 'seed/02.jpg'
    UNION ALL SELECT '이선좌 발라드 콘서트', 'seed/03.jpg'
    UNION ALL SELECT '이선좌 K-POP 앵콜', 'seed/04.jpg'
    UNION ALL SELECT '이선좌 가을 페스티벌', 'seed/05.jpg'
    UNION ALL SELECT '별빛 쇼케이스', 'seed/07.jpg'
    UNION ALL SELECT '언더그라운드 사이퍼', 'seed/08.jpg'
    UNION ALL SELECT '겨울 편지', 'seed/09.jpg'
    UNION ALL SELECT '재즈 인 더 시티', 'seed/10.jpg'
    UNION ALL SELECT '루키 데뷔 콘서트', 'seed/11.jpg'
    UNION ALL SELECT '소울 앤 그루브', 'seed/12.jpg'
    UNION ALL SELECT '오래된 노래', 'seed/13.jpg'
    UNION ALL SELECT '클래식 갈라', 'seed/14.jpg'
    UNION ALL SELECT '네온 시티 라이브', 'seed/15.jpg'
    UNION ALL SELECT '비트 메이커스 나이트', 'seed/16.jpg'
    UNION ALL SELECT '그리움의 온도', 'seed/17.jpg'
    UNION ALL SELECT '인디 밴드 페스티벌', 'seed/18.jpg'
    UNION ALL SELECT '아이돌 페스타', 'seed/19.jpg'
    UNION ALL SELECT '미드나잇 R&B', 'seed/20.jpg'
    UNION ALL SELECT '피아노와 목소리', 'seed/21.jpg'
    UNION ALL SELECT '어쿠스틱 피크닉', 'seed/22.jpg'
    UNION ALL SELECT '드림 스테이지', 'seed/23.jpg'
    UNION ALL SELECT '랩 배틀 파이널', 'seed/24.jpg'
    UNION ALL SELECT '별이 지는 밤', 'seed/25.jpg'
    UNION ALL SELECT '뮤지컬 넘버 콘서트', 'seed/26.jpg'
    UNION ALL SELECT '퍼플 나이트', 'seed/27.jpg'
    UNION ALL SELECT '스트릿 사운드', 'seed/28.jpg'
    UNION ALL SELECT '다시 봄', 'seed/29.jpg'
    UNION ALL SELECT '록 스피릿', 'seed/30.jpg'
    UNION ALL SELECT '스타트라인 콘서트', 'seed/31.jpg'
    UNION ALL SELECT '그루브 시티', 'seed/32.jpg'
    UNION ALL SELECT '작은 위로', 'seed/33.jpg'
    UNION ALL SELECT '시네마 OST 콘서트', 'seed/34.jpg'
    UNION ALL SELECT '하이라이트 리런', 'seed/35.jpg'
    UNION ALL SELECT '블루 노트 세션', 'seed/36.jpg'
    UNION ALL SELECT '우리의 이야기', 'seed/37.jpg'
    UNION ALL SELECT '국악 퓨전 무대', 'seed/38.jpg'
    UNION ALL SELECT '첫눈 콘서트', 'seed/39.jpg'
    UNION ALL SELECT '크루 쇼케이스', 'seed/40.jpg'
    UNION ALL SELECT '고요한 밤의 노래', 'seed/41.jpg'
    UNION ALL SELECT '일렉트로닉 나이트', 'seed/42.jpg'
    UNION ALL SELECT '뉴이어 카운트다운', 'seed/43.jpg'
    UNION ALL SELECT '플로우 페스티벌', 'seed/44.jpg'
    UNION ALL SELECT '기억의 정원', 'seed/45.jpg'
    UNION ALL SELECT '아카펠라 하모니', 'seed/46.jpg'
    UNION ALL SELECT '팬미팅 우리의 계절', 'seed/47.jpg'
    UNION ALL SELECT '레이트 나이트 잼', 'seed/48.jpg'
    UNION ALL SELECT '손편지 콘서트', 'seed/49.jpg'
    UNION ALL SELECT '오케스트라 신년 음악회', 'seed/50.jpg'
    UNION ALL SELECT '글로우 업 투어', 'seed/51.jpg'
    UNION ALL SELECT '붐뱁 리턴즈', 'seed/52.jpg'
    UNION ALL SELECT '따뜻한 겨울', 'seed/53.jpg'
) AS v ON v.title = p.title
SET p.poster_key = v.poster_key
WHERE p.poster_key IS NULL;
