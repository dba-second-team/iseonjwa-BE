# 좌석 선점 동시성 재현

API-6은 `POST /api/schedules/{scheduleId}/seats/hold`입니다. `X-Member-Id`와 `{"seatIds":[1,2]}`를 보내면 `200 OK`와 `data.seatIds`, `data.heldUntil`을 반환합니다. 만료 시각은 한국 시간입니다. 선점은 5분 동안 유효하며, 만료 전에 다시 요청하면 같은 회원도 `409 SEAT_UNAVAILABLE`을 받습니다.

서비스는 회원 행을 먼저 잠그고 좌석 행은 기본 키 오름차순으로 잠급니다. `READ COMMITTED`에서 다른 요청의 커밋을 기다린 뒤 미만료 선점과 확정 예매 좌석을 집계합니다. 검증을 모두 통과하면 같은 트랜잭션에서 요청 좌석을 갱신합니다. 이후 예매 확정과 취소 기능에서 이 한도에 영향을 주는 경우에도 같은 회원 잠금 순서를 사용해야 합니다.

## 자동 테스트

로컬 MySQL에 이 테스트 전용으로 빈 `iseonjwa_hold_test` 데이터베이스를 만듭니다. Flyway가 적용한 샘플 데이터를 테스트가 읽고 일부 상태를 변경합니다. 테스트 DB에만 접근할 계정으로 아래를 실행합니다.

```powershell
$env:ISEONJWA_MYSQL_TEST_URL = 'jdbc:mysql://127.0.0.1:33306/iseonjwa_hold_test?connectTimeout=3000&socketTimeout=10000'
$env:ISEONJWA_MYSQL_TEST_USER = '<테스트 DB 사용자>'
$env:ISEONJWA_MYSQL_TEST_PASSWORD = '<테스트 DB 비밀번호>'
.\gradlew.bat test --rerun-tasks
```

포트는 테스트 서버에 맞춥니다. `ISEONJWA_MYSQL_TEST_URL`을 설정하면 기존 H2 컨텍스트 테스트와 MySQL 선점 테스트를 모두 실행합니다. Gradle 테스트 리포트의 `SeatHoldMySqlTest`에서 선점 결과를 확인하고, 해당 테스트의 로그에서 좌석과 회원 조회 SQL에 `for update`가 붙는지 확인합니다.

## MySQL 세션 두 개로 재현

두 세션에서 `USE iseonjwa_hold_test`를 실행합니다. 세션 A에서 데모 테이블을 준비하고, 각 시나리오를 시작하기 전에 아래 `INSERT`를 다시 실행해 `AVAILABLE` 상태로 되돌립니다.

```sql
CREATE TABLE IF NOT EXISTS seat_hold_demo (
    seat_id BIGINT PRIMARY KEY,
    status VARCHAR(20) NOT NULL
) ENGINE=InnoDB;

INSERT INTO seat_hold_demo (seat_id, status) VALUES (1, 'AVAILABLE')
ON DUPLICATE KEY UPDATE status = 'AVAILABLE';
```

### 일반 조회와 격리 수준

1. 세션 B에서 `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION;`을 실행한 뒤 `SELECT status FROM seat_hold_demo WHERE seat_id = 1;`로 `AVAILABLE`을 읽습니다.
2. 세션 A에서 `START TRANSACTION;`으로 시작하고 같은 `SELECT`로 `AVAILABLE`을 읽습니다. 이어서 `UPDATE seat_hold_demo SET status = 'HELD' WHERE seat_id = 1; COMMIT;`을 실행합니다.
3. 세션 B가 같은 `SELECT`를 실행하면 처음 만든 스냅샷에서 `AVAILABLE`을 다시 읽습니다. 세션 B를 `ROLLBACK;`으로 끝냅니다.
4. 초기 상태를 복원하고 세션 B의 격리 수준을 `READ COMMITTED`로 바꿔 반복합니다. 두 번째 `SELECT`는 A가 커밋한 `HELD`를 읽습니다.

### 잠금 조회와 커밋

1. 세션 A에서 `START TRANSACTION; SELECT status FROM seat_hold_demo WHERE seat_id = 1 FOR UPDATE;`를 실행합니다. 이 트랜잭션에서 `UPDATE seat_hold_demo SET status = 'HELD' WHERE seat_id = 1;`을 실행하고 열린 상태로 둡니다.
2. 세션 B에서 `START TRANSACTION; SELECT status FROM seat_hold_demo WHERE seat_id = 1 FOR UPDATE;`를 실행합니다. A가 잠근 좌석을 기다립니다.
3. 세션 A에서 `COMMIT;`을 실행합니다. B의 조회가 반환하는 값은 `HELD`입니다. API는 이 상태에서 선점을 거절합니다. B에서 `ROLLBACK;`으로 락을 풉니다.

### 잠금 조회와 롤백

같은 순서로 A가 좌석을 갱신하고 B가 기다리게 한 뒤, A에서 `ROLLBACK;`을 실행합니다. B는 `AVAILABLE`을 읽은 상태에서 좌석을 잠그고 있으므로 `UPDATE seat_hold_demo SET status = 'HELD' WHERE seat_id = 1; COMMIT;`으로 선점을 완료할 수 있습니다.

두 잠금 조회 시나리오의 2단계 직전에 세션 B에서 `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;` 또는 `SET TRANSACTION ISOLATION LEVEL READ COMMITTED;`를 실행해 각각 반복합니다. 두 수준 모두 같은 행의 잠금이 풀릴 때까지 기다립니다.
