package secondteam.iseonjwa.queue.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisQueueRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RedisQueueRepository redisQueueRepository;

    @BeforeEach
    void setUp() {
        redisQueueRepository = new RedisQueueRepository(stringRedisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsOneBasedPositionAndPassesExpectedRedisKeysAndArgs() {
        Instant now = Instant.parse("2026-11-22T08:30:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 15L));

        long position = redisQueueRepository.enqueueWaiting(7L, 3L, now);

        assertThat(position).isEqualTo(15L);

        ArgumentCaptor<RedisScript<List>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(stringRedisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), argsCaptor.capture());

        assertThat(keysCaptor.getValue()).containsExactly(
                "queue:member:7",
                "queue:waiting:3",
                "queue:waiting:seq:3"
        );
        assertThat(argsCaptor.getValue()).containsExactly(
                "7",
                "3",
                String.valueOf(now.toEpochMilli()),
                "queue:waiting:",
                "queue:active:"
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("ZRANK")
                .contains("ZADD")
                .contains("HSET");
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsAlreadyInQueueWhenLuaReturnsZeroAdmittedFlag() {
        Instant now = Instant.parse("2026-11-22T08:30:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L));

        assertThatThrownBy(() -> redisQueueRepository.enqueueWaiting(7L, 1L, now))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_IN_QUEUE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsIllegalStateExceptionWhenScriptResultIsInvalid() {
        Instant now = Instant.parse("2026-11-22T08:30:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> redisQueueRepository.enqueueWaiting(7L, 1L, now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsWaitingSnapshotAndPassesExpectedKeysAndArgs() {
        Instant now = Instant.parse("2026-11-22T09:00:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 5L, 3L, 8L));

        Optional<QueueEntrySnapshot> result = redisQueueRepository.findMemberQueueState(11L, now);

        assertThat(result).contains(QueueEntrySnapshot.waiting(5L, 3L, 8L));

        ArgumentCaptor<RedisScript<List>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(stringRedisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), argsCaptor.capture());

        assertThat(keysCaptor.getValue()).containsExactly("queue:member:11");
        assertThat(argsCaptor.getValue()).containsExactly(
                "11",
                String.valueOf(now.toEpochMilli()),
                "queue:waiting:",
                "queue:active:"
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("ZRANK")
                .contains("ZREMRANGEBYSCORE")
                .contains("ZCARD")
                .contains("ZSCORE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsActiveSnapshotWhenLuaReturnsActiveStateCode() {
        Instant now = Instant.parse("2026-11-22T09:00:00Z");
        Instant expiresAt = Instant.parse("2026-11-22T09:40:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(2L, 1L, 0L, expiresAt.toEpochMilli()));

        Optional<QueueEntrySnapshot> result = redisQueueRepository.findMemberQueueState(11L, now);

        assertThat(result).contains(QueueEntrySnapshot.active(1L, expiresAt));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsEmptyWhenLuaReturnsNotFoundStateCode() {
        Instant now = Instant.parse("2026-11-22T09:00:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, 0L, 0L));

        Optional<QueueEntrySnapshot> result = redisQueueRepository.findMemberQueueState(11L, now);

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void entersActiveQueueAndPassesExpectedKeysAndArgs() {
        Instant now = Instant.parse("2026-11-22T09:31:00Z");
        Instant expiresAt = Instant.parse("2026-11-22T09:41:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, now.toEpochMilli(), expiresAt.toEpochMilli()));

        ActiveQueueAdmission admission = redisQueueRepository.enterActiveQueue(9L, 2L, now, expiresAt, 4L);

        assertThat(admission.admittedAt()).isEqualTo(now);
        assertThat(admission.expiresAt()).isEqualTo(expiresAt);

        ArgumentCaptor<RedisScript<List>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(stringRedisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), argsCaptor.capture());

        assertThat(keysCaptor.getValue()).containsExactly(
                "queue:member:9",
                "queue:waiting:2",
                "queue:active:2"
        );
        assertThat(argsCaptor.getValue()).containsExactly(
                "9",
                "2",
                String.valueOf(now.toEpochMilli()),
                String.valueOf(expiresAt.toEpochMilli()),
                "4",
                "queue:waiting:",
                "queue:active:"
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("TIME")
                .contains("ZREMRANGEBYSCORE")
                .contains("ZRANK")
                .contains("ZREM")
                .contains("ZADD")
                .contains("admittedAt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsCannotEnterActiveQueueWhenTargetExpiryAlreadyPassedAtRedisExecutionTime() {
        Instant now = Instant.parse("2026-11-22T09:31:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(-2L, 0L, 0L));

        assertThatThrownBy(() -> redisQueueRepository.enterActiveQueue(9L, 2L, now, now.plusSeconds(1), 4L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_ENTER_ACTIVE_QUEUE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsQueueEntryNotFoundWhenEnterActiveLuaReturnsZero() {
        Instant now = Instant.parse("2026-11-22T09:31:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, 0L));

        assertThatThrownBy(() -> redisQueueRepository.enterActiveQueue(9L, 2L, now, now.plusSeconds(600), 4L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsCannotEnterActiveQueueWhenEnterActiveLuaReturnsMinusOne() {
        Instant now = Instant.parse("2026-11-22T09:31:00Z");
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(-1L, 0L, 0L));

        assertThatThrownBy(() -> redisQueueRepository.enterActiveQueue(9L, 2L, now, now.plusSeconds(600), 4L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_ENTER_ACTIVE_QUEUE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void leavesQueuePassingExpectedKeysAndPrefixes() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        redisQueueRepository.leaveQueue(13L);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(stringRedisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), argsCaptor.capture());

        assertThat(keysCaptor.getValue()).containsExactly("queue:member:13");
        assertThat(argsCaptor.getValue()).containsExactly(
                "13",
                "queue:waiting:",
                "queue:active:"
        );
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("HGET")
                .contains("ZREM")
                .contains("DEL");
    }
}
