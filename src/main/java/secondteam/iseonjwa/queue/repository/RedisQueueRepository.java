package secondteam.iseonjwa.queue.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    /**
     * 원자적 큐 상태 통합 조회 스크립트.
     * <p>
     * 1) 회원의 전역 상태 해시(queue:member:{memberId})가 없거나 ZSet 실제 상태와 불일치·만료된 경우
     *    잔여 키를 정리하고 {0, 0, 0, 0} 반환.<br>
     * 2) WAITING 상태면 해당 공연 active_queue의 만료 엔트리를 정리한 뒤
     *    {1, performanceId, 1-based position, validActiveCount} 반환.<br>
     * 3) ACTIVE 상태이고 만료 전(expiresAt > nowMillis)이면 {2, performanceId, 0, expiresAtMillis} 반환.
     */
    private static final String GET_QUEUE_STATUS_LUA = """
            local memberKey = KEYS[1]

            local memberId = ARGV[1]
            local nowMillis = tonumber(ARGV[2])
            local waitingPrefix = ARGV[3]
            local activePrefix = ARGV[4]

            local currentStatus = redis.call('HGET', memberKey, 'status')
            local performanceId = redis.call('HGET', memberKey, 'performanceId')

            if not currentStatus or not performanceId then
                if currentStatus or performanceId then
                    redis.call('DEL', memberKey)
                end
                return {0, 0, 0, 0}
            end

            if currentStatus == 'WAITING' then
                local waitingKey = waitingPrefix .. performanceId
                local rank = redis.call('ZRANK', waitingKey, memberId)
                if rank == false then
                    redis.call('DEL', memberKey)
                    return {0, 0, 0, 0}
                end

                local activeKey = activePrefix .. performanceId
                redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', nowMillis)
                local validActiveCount = redis.call('ZCARD', activeKey)
                return {1, tonumber(performanceId), rank + 1, validActiveCount}
            end

            if currentStatus == 'ACTIVE' then
                local activeKey = activePrefix .. performanceId
                local expiresAt = redis.call('ZSCORE', activeKey, memberId)
                if expiresAt == false or tonumber(expiresAt) <= nowMillis then
                    redis.call('ZREM', activeKey, memberId)
                    redis.call('DEL', memberKey)
                    return {0, 0, 0, 0}
                end

                return {2, tonumber(performanceId), 0, math.floor(tonumber(expiresAt))}
            end

            redis.call('DEL', memberKey)
            return {0, 0, 0, 0}
            """;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ENQUEUE_WAITING_SCRIPT = createScript(ENQUEUE_WAITING_LUA);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> GET_QUEUE_STATUS_SCRIPT = createScript(GET_QUEUE_STATUS_LUA);

    /**
     * 원자적 예매 작업열 진입 스크립트.
     * <p>
     * 1) Redis 실행 시점 시각(TIME)과 요청 시각 중 최신값을 기준으로 active_queue 만료 엔트리를 먼저 제거.<br>
     * 2) 이미 같은 공연의 유효한 ACTIVE 상태라면 타이머 연장 없이 기존 {1, admittedAt, expiresAt} 반환(멱등 처리).<br>
     * 3) 대상 공연의 WAITING 상태가 아니거나 만료·유실된 상태라면 잔여 데이터를 정리하고 {0, 0, 0} 반환(QUEUE_ENTRY_NOT_FOUND).<br>
     * 4) 스크립트 실행 시점에 이미 expiresAtMillis가 지났다면 대기열 순번을 유지한 채 {-2, 0, 0} 반환.<br>
     * 5) 대기 순번(ZRANK + 1)이 남은 작업열 자리 수(maxActiveSize - validActiveCount) 이내일 때만
     *    waiting_queue에서 제거하고 active_queue 및 회원 해시를 ACTIVE로 갱신하여 {1, nowMillis, expiresAtMillis} 반환.
     *    순번이 아직 안 되었거나 정원 초과면 {-1, 0, 0} 반환(CANNOT_ENTER_ACTIVE_QUEUE).
     */
    private static final String ENTER_ACTIVE_LUA = """
            local memberKey = KEYS[1]
            local waitingKey = KEYS[2]
            local activeKey = KEYS[3]

            local memberId = ARGV[1]
            local performanceId = ARGV[2]
            local nowMillis = tonumber(ARGV[3])
            local expiresAtMillis = tonumber(ARGV[4])
            local maxActiveSize = tonumber(ARGV[5])
            local waitingPrefix = ARGV[6]
            local activePrefix = ARGV[7]

            local redisTime = redis.call('TIME')
            local execNowMillis = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)
            local effectiveNowMillis = math.max(nowMillis, execNowMillis)

            redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', effectiveNowMillis)

            local currentStatus = redis.call('HGET', memberKey, 'status')
            local prevPerfId = redis.call('HGET', memberKey, 'performanceId')

            if currentStatus == 'ACTIVE' and prevPerfId then
                local prevActiveKey = activePrefix .. prevPerfId
                local existingExpiresAt = redis.call('ZSCORE', prevActiveKey, memberId)
                if existingExpiresAt == false or tonumber(existingExpiresAt) <= effectiveNowMillis then
                    redis.call('ZREM', prevActiveKey, memberId)
                    redis.call('DEL', memberKey)
                    return {0, 0, 0}
                end
                if prevPerfId == performanceId then
                    local existingAdmittedAt = redis.call('HGET', memberKey, 'admittedAt')
                    local admittedMillis = existingAdmittedAt and tonumber(existingAdmittedAt) or nowMillis
                    return {1, math.floor(admittedMillis), math.floor(tonumber(existingExpiresAt))}
                end
                return {0, 0, 0}
            end

            if currentStatus ~= 'WAITING' or prevPerfId ~= performanceId then
                if currentStatus == 'WAITING' and prevPerfId then
                    if redis.call('ZSCORE', waitingPrefix .. prevPerfId, memberId) == false then
                        redis.call('DEL', memberKey)
                    end
                elseif currentStatus or prevPerfId then
                    redis.call('DEL', memberKey)
                end
                return {0, 0, 0}
            end

            local rank = redis.call('ZRANK', waitingKey, memberId)
            if rank == false then
                redis.call('DEL', memberKey)
                return {0, 0, 0}
            end

            if expiresAtMillis <= effectiveNowMillis then
                return {-2, 0, 0}
            end

            local validActiveCount = redis.call('ZCARD', activeKey)
            local availableSlots = maxActiveSize - validActiveCount
            local position = rank + 1

            if availableSlots <= 0 or position > availableSlots then
                return {-1, 0, 0}
            end

            redis.call('ZREM', waitingKey, memberId)
            redis.call('ZADD', activeKey, expiresAtMillis, memberId)
            redis.call('DEL', memberKey)
            redis.call('HSET', memberKey,
                'performanceId', performanceId,
                'status', 'ACTIVE',
                'admittedAt', tostring(nowMillis),
                'expiresAt', tostring(expiresAtMillis)
            )

            return {1, nowMillis, expiresAtMillis}
            """;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ENTER_ACTIVE_SCRIPT = createScript(ENTER_ACTIVE_LUA);

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

    @Override
    public Optional<QueueEntrySnapshot> findMemberQueueState(long memberId, Instant now) {
        List<String> keys = List.of(RedisQueueKeys.memberStateKey(memberId));

        List<?> result = stringRedisTemplate.execute(
                GET_QUEUE_STATUS_SCRIPT,
                keys,
                String.valueOf(memberId),
                String.valueOf(now.toEpochMilli()),
                RedisQueueKeys.WAITING_QUEUE_PREFIX,
                RedisQueueKeys.ACTIVE_QUEUE_PREFIX
        );

        if (result == null || result.size() < 4) {
            throw new IllegalStateException("큐 상태 조회 스크립트 실행 결과가 올바르지 않습니다.");
        }

        long stateCode = ((Number) result.get(0)).longValue();
        if (stateCode == 0L) {
            return Optional.empty();
        }

        long performanceId = ((Number) result.get(1)).longValue();
        if (stateCode == 1L) {
            long position = ((Number) result.get(2)).longValue();
            long validActiveCount = ((Number) result.get(3)).longValue();
            return Optional.of(QueueEntrySnapshot.waiting(performanceId, position, validActiveCount));
        }
        if (stateCode == 2L) {
            long expiresAtMillis = ((Number) result.get(3)).longValue();
            return Optional.of(QueueEntrySnapshot.active(performanceId, Instant.ofEpochMilli(expiresAtMillis)));
        }

        throw new IllegalStateException("알 수 없는 큐 상태 코드입니다: " + stateCode);
    }

    @Override
    public ActiveQueueAdmission enterActiveQueue(
            long memberId,
            long performanceId,
            Instant now,
            Instant expiresAt,
            long maxActiveSize) {
        if (!expiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.CANNOT_ENTER_ACTIVE_QUEUE);
        }

        List<String> keys = List.of(
                RedisQueueKeys.memberStateKey(memberId),
                RedisQueueKeys.waitingQueueKey(performanceId),
                RedisQueueKeys.activeQueueKey(performanceId)
        );

        List<?> result = stringRedisTemplate.execute(
                ENTER_ACTIVE_SCRIPT,
                keys,
                String.valueOf(memberId),
                String.valueOf(performanceId),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(expiresAt.toEpochMilli()),
                String.valueOf(maxActiveSize),
                RedisQueueKeys.WAITING_QUEUE_PREFIX,
                RedisQueueKeys.ACTIVE_QUEUE_PREFIX
        );

        if (result == null || result.size() < 3) {
            throw new IllegalStateException("작업열 진입 스크립트 실행 결과가 올바르지 않습니다.");
        }

        long stateCode = ((Number) result.get(0)).longValue();
        if (stateCode == 0L) {
            throw new BusinessException(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
        }
        if (stateCode == -1L || stateCode == -2L) {
            throw new BusinessException(ErrorCode.CANNOT_ENTER_ACTIVE_QUEUE);
        }
        if (stateCode == 1L) {
            long admittedMillis = ((Number) result.get(1)).longValue();
            long expiresMillis = ((Number) result.get(2)).longValue();
            return new ActiveQueueAdmission(
                    Instant.ofEpochMilli(admittedMillis),
                    Instant.ofEpochMilli(expiresMillis)
            );
        }

        throw new IllegalStateException("알 수 없는 작업열 진입 결과 코드입니다: " + stateCode);
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> createScript(String lua) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);
        return script;
    }
}
