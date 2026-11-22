ALTER TABLE price DROP CHECK CHK_PRICE_RANK;

START TRANSACTION;

-- 공연마다 기존 등급의 가격을 대응하는 구역에 복사
INSERT INTO price (performance_id, seat_rank, price)
SELECT old_price.performance_id, section_map.section, old_price.price
FROM price AS old_price
JOIN (
    SELECT 'FS' AS old_rank, 'F1' AS section
    UNION ALL SELECT 'FS', 'F2'
    UNION ALL SELECT 'FS', 'F3'
    UNION ALL SELECT 'FS', 'F4'
    UNION ALL SELECT 'VIP', 'S1'
    UNION ALL SELECT 'VIP', 'S2'
    UNION ALL SELECT 'R', 'G'
) AS section_map ON old_price.seat_rank = section_map.old_rank;

DELETE FROM price
WHERE seat_rank IN ('FS', 'VIP', 'R');

COMMIT;

-- 컬럼 이름을 바꾸면 가격의 복합 기본키가 (performance_id, section)이 됨
ALTER TABLE price RENAME COLUMN seat_rank TO section;

ALTER TABLE price
    ADD CONSTRAINT CHK_PRICE_SECTION
    CHECK (section IN ('F1', 'F2', 'F3', 'F4', 'S1', 'S2', 'G'));

ALTER TABLE seat
    DROP CHECK CHK_SEAT_RANK,
    DROP COLUMN seat_rank;
