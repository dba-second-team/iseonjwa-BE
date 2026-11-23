# k6 동시성 테스트

대기열, 좌석 선점, 결제, 취소에 요청이 동시에 몰려도 정원 초과, 중복 선점, 한도 초과, 데드락, 중복 결제, 중복 취소가 생기지 않는지 확인하는 스크립트입니다.
CI에서는 돌리지 않고, 필요할 때 노트북에서 직접 실행합니다.

## 파일 구성

| 파일 | 확인하는 것 | 합격 기준 |
|---|---|---|
| `signup-race.js` | 같은 이메일로 50명이 동시에 회원가입 | 성공 1건, 나머지 409 `EMAIL_DUPLICATED` |
| `seat-hold-race.js` | (A) 회원 100명이 같은 좌석 1개를 동시에 선점 | 성공 1건, 나머지 409 `SEAT_UNAVAILABLE` |
| `hold-limit.js` | (B) 회원 1명이 서로 다른 좌석 10개를 동시에 선점 | 한도(4)만큼 성공, 나머지 409 `HOLD_LIMIT_EXCEEDED` |
| `deadlock.js` | (C) 좌석 두 개를 [A, B], [B, A] 순서로 섞어 동시에 선점 | 성공 1건, 500 0건 |
| `queue-flow.js` | (D) 회원 100명이 대기열 등록 → 동시 입장 → 입장한 사람끼리 같은 좌석 선점 → 결제 → 빈자리에 다음 대기자 입장 | 100명 등록, 4명만 입장(순번 1~4), 선점 1건, 결제 1건, 재입장 1명 |
| `pay-race.js` | (E) 같은 회원이 같은 좌석 결제를 10번 동시에 요청 (결제 버튼 연타) | 예매 1건, 나머지 409 `HOLD_EXPIRED` |
| `cancel-race.js` | (F) 같은 예매에 취소 요청 10건 동시 | 취소 1건, 나머지 409 `ALREADY_CANCELED` |
| `lib/common.js` | 로그인, 대기열, 선점, 결제, 취소 API 호출 같은 공통 함수 | |
| `sql/find-targets.sql` | k6에 넘길 회차 ID, 좌석 ID 조회 | |
| `sql/verify.sql` | 테스트 후 DB 정합성 확인 | 1~3번 쿼리 결과가 0행 |

데이터 준비는 `src/main/resources/db/migration/V6__add_demo_seed.sql`(기본 데이터)과
`scripts/demo-reset.sql`(예매 중인 공연을 시연 상태로 되돌리기)이 맡습니다.

API 명세가 바뀌면 `lib/common.js`의 해당 함수(`holdSeats()`, `registerWaiting()`, `confirmReservation()` 등)만 고치면 됩니다.

A, B, C는 선점 API를 바로 호출합니다. 서버의 선점 API는 작업열 입장 여부를 확인하지 않으므로, 대기열 없이도 DB 락만으로 정확성이 지켜지는지 보는 테스트입니다.
D는 화면과 같은 순서(대기열 → 입장 → 선점 → 결제)로 호출해 대기열과 락이 함께 동작하는지 봅니다.

## 설치

```bash
brew install k6                       # macOS
winget install k6 --source winget     # Windows
k6 version
```

## 실행 순서

### 1. DB를 시연 상태로 되돌린다

DB에 접속할 수 있는 도구(DBeaver, MySQL Workbench, EC2의 mysql 클라이언트 등)에서 실행합니다.

```bash
mysql -h <DB 주소> -u <사용자> -p iseonjwa < scripts/demo-reset.sql
```

마지막에 회차별 좌석 현황이 나오면 성공입니다. 대상 공연이나 회원을 못 찾으면
`Subquery returns more than 1 row` 에러를 내고 아무것도 바꾸지 않은 채 멈춥니다. 이때는 V6이 적용됐는지 확인하세요.

### 2. 대상 ID를 찾는다

```bash
mysql -h <DB 주소> -u <사용자> -p iseonjwa < k6/sql/find-targets.sql
```

`PERFORMANCE_ID`, `SCHEDULE_ID`, `SEAT_ID`, `SEAT_PAIR`, `SEAT_IDS`, `FLOW_SEAT_ID`, `PAY_SEAT_IDS`, `CANCEL_SEAT_IDS`가 나옵니다. **DB마다 ID가 다르니 매번 조회한 값을 쓰세요.**
대상은 K-POP LIVE 2027-01-17 회차이고, 테스트 전용이라 시연 화면에 쓰지 않습니다.

### 3. k6를 실행한다

`BASE`는 로컬이면 `http://localhost:8080`, 배포 서버면 `http://<배포 주소>`입니다(Nginx가 `/api`를 백엔드로 넘깁니다).

```bash
# 회원가입 경쟁 (지금 바로 가능)
k6 run -e BASE=http://localhost:8080 k6/signup-race.js

# A. 같은 좌석 경쟁
k6 run -e BASE=... -e SCHEDULE_ID=2 -e SEAT_ID=3019 k6/seat-hold-race.js

# B. 1인 매수 한도
k6 run -e BASE=... -e SCHEDULE_ID=2 -e SEAT_IDS=2123,2137,2152,2167,2182,2197,2212,2227,2242,2257 k6/hold-limit.js

# C. 데드락
k6 run -e BASE=... -e SCHEDULE_ID=2 -e SEAT_PAIR=3019,3034 k6/deadlock.js

# D. 대기열부터 결제까지
k6 run -e BASE=... -e PERFORMANCE_ID=1 -e SCHEDULE_ID=2 -e FLOW_SEAT_ID=3018 k6/queue-flow.js

# E. 결제 중복 요청
k6 run -e BASE=... -e SCHEDULE_ID=2 -e PAY_SEAT_IDS=2122,2136 k6/pay-race.js

# F. 취소 중복 요청
k6 run -e BASE=... -e SCHEDULE_ID=2 -e CANCEL_SEAT_IDS=2121,7 k6/cancel-race.js
```

`export SCHEDULE_ID=2`처럼 환경 변수로 먼저 넣어 두면 `-e`를 빼고 `k6 run k6/deadlock.js`만 쳐도 됩니다.
`-e SCHEDULE_ID`처럼 값 없이 이름만 쓰면 빈 값으로 덮어쓰니 주의하세요.

시나리오마다 앞 시나리오가 잡아 둔 선점이나 만든 예매가 남아 있으니, **시나리오 사이에 1번(demo-reset.sql)을 다시 실행**하세요.

D는 대기열(Redis)을 씁니다. 다른 사람이 같은 공연 대기열에 들어와 있으면 정원 계산이 어긋나니, 아무도 예매 화면을 쓰지 않을 때 실행하세요.
테스트에 쓴 회원(load201~load300)은 끝날 때 대기열에서 자동으로 뺍니다.
서버의 작업열 정원(`queue.active.max-size`)을 바꿨다면 `-e MAX_ACTIVE=정원`을 같이 넘기세요.

| 시나리오 | 쓰는 회원 | 쓰는 좌석 (K-POP LIVE 1/17) |
|---|---|---|
| A | load001~load100 | F1 1열 1번 |
| B | load001 | G 1열 1~10번 |
| C | load001~load020 | F1 1열 1, 2번 |
| D | load201~load300 | F1 2열 1번 |
| E | load101 | G 2열 1, 2번 |
| F | load102 | G 3열 1, 2번 |

### 4. 결과를 확인한다

터미널 요약의 `THRESHOLDS`가 전부 ✓이면 합격이고, k6가 종료 코드 0으로 끝납니다.
하나라도 ✗이면 종료 코드 99로 끝납니다.

| 항목 | 의미 |
|---|---|
| `hold_success`, `pay_success`, `cancel_success`, `first_admitted`, `late_admitted` | 성공한 요청 수. 시나리오별 합격 기준과 같아야 함 |
| `admitted_out_of_turn` | (D) 대기 순번이 정원 밖인데 입장한 수. 0이어야 함 |
| `http_req_failed{name:...}` | 200, 201, 409를 뺀 응답 비율. 500(데드락, 락 대기 초과)이 있으면 0%가 아님 |
| `checks` | 409일 때 에러 코드까지 맞았는지 |
| `http_req_duration{name:hold}` | 선점 요청 응답 시간. 발표 때 p(95) 값을 씁니다 |

응답만 믿지 말고 DB도 확인합니다.

```bash
mysql -h <DB 주소> -u <사용자> -p iseonjwa < k6/sql/verify.sql
```

1~5번 쿼리가 0행이면 정상이고, 6번은 대상 회차에서 선점된 좌석 목록입니다(발표 캡처용).
1번은 중복 예매, 2번은 좌석 상태와 예매, 3번은 선점 정보, 4번은 회차별 매수 한도, 5번은 결제 상태와 금액을 확인합니다.

### 5. 끝나면 다시 되돌린다

테스트 후, 그리고 **발표 시연 직전**에 `scripts/demo-reset.sql`을 실행합니다.
시연용 "선점 중" 좌석은 실행 시점부터 5분 뒤 만료되니 시연 바로 전에 실행하세요.

## 발표용 HTML 리포트

```bash
K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_PERIOD=1s K6_WEB_DASHBOARD_EXPORT=report-a.html \
  k6 run -e BASE=... -e SCHEDULE_ID=... -e SEAT_ID=... k6/seat-hold-race.js
```

테스트 시간이 집계 주기의 3배보다 짧으면 그래프가 비어서 `PERIOD`를 1초로 줄였습니다.

## 락을 빼면 어떻게 되나 (발표 비교 자료)

같은 스크립트를 락이 없는 버전에 돌리면 테스트가 실패해야 정상입니다. 검증용 서버에서 확인한 결과는 다음과 같습니다.

| 구현 | A. 같은 좌석 100명 | C. 순서 섞인 두 좌석 |
|---|---|---|
| `FOR UPDATE` 없음 | 성공 4건 ✗ | 성공 5건 ✗ |
| 요청 순서대로 잠금 | 성공 1건 ✓ | 데드락으로 500 발생 ✗ |
| ID 순 잠금 + 회원 행 잠금 | 성공 1건 ✓ | 성공 1건, 500 0건 ✓ |

락을 잠깐 뺀 브랜치에서 A를 한 번 돌려 결과를 캡처해 두면
"왜 비관적 락을 썼나요?"라는 질문에 숫자로 답할 수 있습니다. 그 브랜치는 머지하지 않습니다.

## 데이터 메모

- 테스트 계정 비밀번호는 전부 `Demo1234!`입니다.
- 결제 상태: `PAID` 확정, `CANCELED` 환불 진행 중, `REFUNDED` 환불 완료. 좌석은 취소할 때 바로 반환됩니다(DFD 10.0).
  취소 수수료는 #27 정책(7일 이상 0%, 3~6일 10%, 1~2일 30%)대로 계산해 넣었습니다.
- 좌석은 관리자 공연 등록(#25)과 같은 배치로 회차마다 1,050석(7구역 x 15열 x 10석)입니다.
- 공연 53개 중 52개에 포스터 키(`posters/seed/NN.jpg`)가 있습니다. 이미지는 S3 버킷의 `posters/seed/` 아래에 올려 두었고,
  원본은 `scripts/seed-posters/`에 있습니다. `코미디 뮤직 쇼`는 기본 이미지 확인용으로 포스터가 없습니다.
- 계정별 용도

| 계정 | 용도 |
|---|---|
| `demo.user` | 예매 내역 48건(확정 31, 환불 진행 중 7, 환불 완료 10). 20건씩 3페이지 |
| `demo.limit` | K-POP LIVE 1/16에 3석 확정. 1석은 더 잡히고 그다음은 `HOLD_LIMIT_EXCEEDED` |
| `demo.admin` | 관리자 |
| `team1`~`team5` | 팀원 수동 테스트. 예매 기록 없음 |
| `load001`~`load100` | k6 전용 (K-POP LIVE 1/17 회차에 기록 없음) |
| `load199`, `load200` | K-POP LIVE 1/16 선점 시연 좌석의 주인 |
| `load301`~`load340` | K-POP LIVE 1/16 관리자용 예매 40건 |
| `load001`~`load523` | HIPHOP 2/6 매진 직전 회차 예매 (k6와 회차가 달라 겹치지 않음) |
| `load001`~`load180` | 스크롤용 공연의 관리자 조회용 예매 (회차마다 앞에서부터 필요한 만큼) |

- 관리자 예매 조회용 데이터 (공연별, 결제 상태별 20건씩)

| 공연 | 확정 | 환불 진행 중 | 환불 완료 |
|---|---|---|---|
| 언더그라운드 사이퍼 (인기 공연, 회차 2개) | 253 | 72 | 36 |
| 이선좌 HIPHOP & R&B NIGHT (매진 직전) | 524 | 0 | 0 |
| 이선좌 K-POP 앵콜 (매진) | 263 | 0 | 0 |
| 이선좌 K-POP LIVE | 33 | 7 | 4 |
| 나머지 예매 중, 종료 공연 41개 | 공연마다 5~41건 | | |

종료 공연에는 환불 진행 중 대신 환불 완료만 있고, 공연 2~5일 전 취소라 수수료(10%, 30%)가 붙어 있습니다.
K-POP LIVE, HIPHOP의 예매는 `demo-reset.sql`을 실행해야 생깁니다.

- 검증 환경: k6 v1.0.0, MySQL 8.0