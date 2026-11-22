-- ============================================
-- k6 실행 후, 또는 시드를 바꾼 뒤 DB가 정합한지 확인
-- 1~5번은 0행이 나와야 정상이다. 한 행이라도 나오면 동시성 버그거나 데이터가 어긋난 것이다.
-- "좌석을 점유한 예매" = 결제가 PAID(확정)인 예매. 취소하면 좌석은 바로 반환된다(DFD 10.0)
-- ============================================

SET NAMES utf8mb4;
SET time_zone = '+09:00';  -- 앱이 한국 시간으로 저장하므로 NOW()도 한국 시간으로

-- 1. 같은 좌석을 점유한 예매가 두 건 이상이다 (중복 예매)
SELECT rs.seat_id, COUNT(*) AS occupying_reservations
FROM reservation_seat AS rs
JOIN payment AS pay ON pay.reservation_id = rs.reservation_id
WHERE pay.status = 'PAID'
GROUP BY rs.seat_id
HAVING COUNT(*) > 1;

-- 2. 좌석 상태와 예매가 맞지 않는다
--    RESERVED인데 점유한 예매가 없거나, 점유한 예매가 있는데 RESERVED가 아니다
SELECT st.seat_id, st.status, COUNT(pay.payment_id) AS occupying_reservations
FROM seat AS st
LEFT JOIN reservation_seat AS rs ON rs.seat_id = st.seat_id
LEFT JOIN payment AS pay ON pay.reservation_id = rs.reservation_id AND pay.status = 'PAID'
GROUP BY st.seat_id, st.status
HAVING (st.status = 'RESERVED') <> (COUNT(pay.payment_id) > 0);

-- 3. 좌석 상태와 선점 정보가 맞지 않는다
SELECT seat_id, status, held_by, held_until
FROM seat
WHERE (status = 'HELD' AND (held_by IS NULL OR held_until IS NULL))
   OR (status <> 'HELD' AND (held_by IS NOT NULL OR held_until IS NOT NULL));

-- 4. 회원 한 명이 한 회차에서 한도보다 많이 잡았다
--    선점 API(#23)와 같은 기준: 만료 전 선점 + 확정(CONFIRMED) 예매 좌석, 회차 단위
SELECT t.member_id, t.schedule_id, p.max_tickets_per_member, SUM(t.cnt) AS total
FROM (
    SELECT held_by AS member_id, schedule_id, COUNT(*) AS cnt
    FROM seat
    WHERE status = 'HELD' AND held_until > NOW()
    GROUP BY held_by, schedule_id
    UNION ALL
    SELECT r.member_id, r.schedule_id, COUNT(DISTINCT rs.seat_id)
    FROM reservation AS r
    JOIN reservation_seat AS rs ON rs.reservation_id = r.reservation_id
    WHERE r.status = 'CONFIRMED'
    GROUP BY r.member_id, r.schedule_id
) AS t
JOIN schedule AS s ON s.schedule_id = t.schedule_id
JOIN performance AS p ON p.performance_id = s.performance_id
GROUP BY t.member_id, t.schedule_id, p.max_tickets_per_member
HAVING SUM(t.cnt) > p.max_tickets_per_member;

-- 5. 예매 상태, 결제 상태, 금액이 맞지 않는다
SELECT r.reservation_id, r.status AS reservation_status, pay.status AS payment_status,
       pay.paid_amount, pay.cancel_fee, pay.refund_amount, r.canceled_at, pay.refunded_at
FROM reservation AS r
JOIN payment AS pay ON pay.reservation_id = r.reservation_id
WHERE (pay.status = 'PAID' AND (r.status <> 'CONFIRMED' OR r.canceled_at IS NOT NULL
                                OR pay.refunded_at IS NOT NULL OR pay.cancel_fee <> 0))
   OR (pay.status = 'CANCELED' AND (r.status <> 'CANCELED' OR r.canceled_at IS NULL
                                    OR pay.refunded_at IS NOT NULL))
   OR (pay.status = 'REFUNDED' AND (r.status <> 'CANCELED' OR r.canceled_at IS NULL
                                    OR pay.refunded_at IS NULL))
   OR (pay.status IN ('CANCELED', 'REFUNDED')
       AND (pay.cancel_fee < 0 OR pay.cancel_fee > pay.paid_amount
            OR pay.refund_amount <> pay.paid_amount - pay.cancel_fee));

-- 6. k6 대상 회차에서 비어 있지 않은 좌석 (테스트 결과 캡처용)
SELECT st.seat_id, st.section, st.row_num, st.seat_num, st.status, st.held_by, st.held_until
FROM seat AS st
JOIN schedule AS s ON s.schedule_id = st.schedule_id
JOIN performance AS p ON p.performance_id = s.performance_id
WHERE p.title = '이선좌 K-POP LIVE'
  AND s.start_at = '2027-01-17 18:00:00'
  AND st.status <> 'AVAILABLE'
ORDER BY st.seat_id;
