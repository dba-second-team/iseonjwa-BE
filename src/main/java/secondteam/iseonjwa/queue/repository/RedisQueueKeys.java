package secondteam.iseonjwa.queue.repository;

/**
 * 대기열·예매 작업열 전체 API(4.1 등록, 4.2 통합 상태 조회, 4.3 작업열 진입, 4 퇴장)에서 공통으로 사용하는 Redis 키 규칙.
 * <ul>
 *   <li>{@code queue:waiting:{performanceId}} (ZSet) — 공연별 대기열 (member: memberId, score: 진입 순서 시퀀스)</li>
 *   <li>{@code queue:waiting:seq:{performanceId}} (String) — 공연별 대기열 진입 순서 보장을 위한 단조 증가 시퀀스</li>
 *   <li>{@code queue:active:{performanceId}} (ZSet) — 공연별 예매 작업열 (member: memberId, score: expiresAt epoch ms)</li>
 *   <li>{@code queue:member:{memberId}} (Hash) — 회원 현재 큐 상태 인덱스
 *       (1계정 1곳 중복 진입 차단, {@code GET /api/queue/status} 및 {@code DELETE /api/queue}의 O(1) 공연 역조회용)</li>
 * </ul>
 */
public final class RedisQueueKeys {

    public static final String WAITING_QUEUE_PREFIX = "queue:waiting:";
    public static final String WAITING_SEQ_PREFIX = "queue:waiting:seq:";
    public static final String ACTIVE_QUEUE_PREFIX = "queue:active:";
    public static final String MEMBER_STATE_PREFIX = "queue:member:";

    public static final String FIELD_PERFORMANCE_ID = "performanceId";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_ENTERED_AT = "enteredAt";
    public static final String FIELD_ADMITTED_AT = "admittedAt";
    public static final String FIELD_EXPIRES_AT = "expiresAt";

    private RedisQueueKeys() {
    }

    public static String waitingQueueKey(long performanceId) {
        return WAITING_QUEUE_PREFIX + performanceId;
    }

    public static String waitingSeqKey(long performanceId) {
        return WAITING_SEQ_PREFIX + performanceId;
    }

    public static String activeQueueKey(long performanceId) {
        return ACTIVE_QUEUE_PREFIX + performanceId;
    }

    public static String memberStateKey(long memberId) {
        return MEMBER_STATE_PREFIX + memberId;
    }
}
