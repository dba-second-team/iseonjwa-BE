package secondteam.iseonjwa.queue.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RedisQueueRepository implements QueueRepository {

    /**
     * 원자적 대기열 진입 스크립트.
     * <p>
     * 1) 회원의 전역 상태 해시(queue:member:{memberId})를 조회하여 같은 공연이든 다른 공연이든
     *    waiting_queue에 있거나 아직 만료되지 않은 active_queue(expiresAt > nowMillis)에 있으면 {0, 0} 반환.<br>
     * 2) 이전 active_queue 세션이 이미 만료(expiresAt <= nowMillis)된 상태라면 잔여 데이터를 정리하고 재진입 허용.<br>
     * 3) 공연별 시퀀스를 증가시켜 진입 순서대로 waiting_queue(ZSet)에 적재하고 1부터 시작하는 순번(ZRANK + 1)을 반환.
     */
    private static final String ENQUEUE_WAITING_LUA = """
            local memberKey = KEYS[1]
            local waitingKey = KEYS[2]
            local seqKey = KEYS[3]

            local memberId = ARGV[1]
            local performanceId = ARGV[2]
            local nowMillis = tonumber(ARGV[3])
            local waitingPrefix = ARGV[4]
            local activePrefix = ARGV[5]

            local currentStatus = redis.call('HGET', memberKey, 'status')
            local prevPerfId = redis.call('HGET', memberKey, 'performanceId')

            if currentStatus == 'WAITING' and prevPerfId then
                local waitingScore = redis.call('ZSCORE', waitingPrefix .. prevPerfId, memberId)
                if waitingScore ~= false then
                    return {0, 0}
                end
                redis.call('DEL', memberKey)
            elseif currentStatus == 'ACTIVE' and prevPerfId then
                local prevActiveKey = activePrefix .. prevPerfId
                local expiresAt = redis.call('ZSCORE', prevActiveKey, memberId)
                if expiresAt ~= false and tonumber(expiresAt) > nowMillis then
                    return {0, 0}
                end
                redis.call('ZREM', prevActiveKey, memberId)
                redis.call('DEL', memberKey)
            end

            if redis.call('ZSCORE', waitingKey, memberId) ~= false then
                return {0, 0}
            end

            local sameActiveKey = activePrefix .. performanceId
            local sameActiveExpires = redis.call('ZSCORE', sameActiveKey, memberId)
            if sameActiveExpires ~= false then
                if tonumber(sameActiveExpires) > nowMillis then
                    return {0, 0}
                end
                redis.call('ZREM', sameActiveKey, memberId)
            end

            local seq = redis.call('INCR', seqKey)
            redis.call('ZADD', waitingKey, seq, memberId)
            redis.call('DEL', memberKey)
            redis.call('HSET', memberKey,
                'performanceId', performanceId,
                'status', 'WAITING',
                'enteredAt', tostring(nowMillis)
            )

            local rank = redis.call('ZRANK', waitingKey, memberId)
            return {1, rank + 1}
            """;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ENQUEUE_WAITING_SCRIPT = createEnqueueScript();

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public long enqueueWaiting(long memberId, long performanceId, Instant now) {
        List<String> keys = List.of(
                RedisQueueKeys.memberStateKey(memberId),
                RedisQueueKeys.waitingQueueKey(performanceId),
                RedisQueueKeys.waitingSeqKey(performanceId)
        );

        List<?> result = stringRedisTemplate.execute(
                ENQUEUE_WAITING_SCRIPT,
                keys,
                String.valueOf(memberId),
                String.valueOf(performanceId),
                String.valueOf(now.toEpochMilli()),
                RedisQueueKeys.WAITING_QUEUE_PREFIX,
                RedisQueueKeys.ACTIVE_QUEUE_PREFIX
        );

        if (result == null || result.size() < 2) {
            throw new IllegalStateException("대기열 등록 스크립트 실행 결과가 올바르지 않습니다.");
        }

        long admitted = ((Number) result.get(0)).longValue();
        if (admitted == 0L) {
            throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
        }

        return ((Number) result.get(1)).longValue();
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> createEnqueueScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(ENQUEUE_WAITING_LUA);
        script.setResultType(List.class);
        return script;
    }
}
