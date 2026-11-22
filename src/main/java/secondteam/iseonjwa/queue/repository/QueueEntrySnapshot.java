package secondteam.iseonjwa.queue.repository;

import secondteam.iseonjwa.queue.dto.QueueStatus;

import java.time.Instant;

/**
 * Redis에서 원자적으로 조회한 회원의 현재 큐 상태 스냅샷.
 *
 * @param performanceId    진입 중인 공연 ID
 * @param status           현재 큐 상태 ({@link QueueStatus#WAITING} 또는 {@link QueueStatus#ACTIVE})
 * @param position         대기열({@code WAITING})일 때 1-based 대기 순번, 작업열({@code ACTIVE})일 때 0
 * @param validActiveCount 대기열({@code WAITING})일 때 해당 공연의 유효(미만료) 작업열 인원수, 작업열({@code ACTIVE})일 때 0
 * @param expiresAt        작업열({@code ACTIVE})일 때 만료 시각, 대기열({@code WAITING})일 때 {@code null}
 */
public record QueueEntrySnapshot(
        long performanceId,
        QueueStatus status,
        long position,
        long validActiveCount,
        Instant expiresAt
) {

    public static QueueEntrySnapshot waiting(long performanceId, long position, long validActiveCount) {
        return new QueueEntrySnapshot(performanceId, QueueStatus.WAITING, position, validActiveCount, null);
    }

    public static QueueEntrySnapshot active(long performanceId, Instant expiresAt) {
        return new QueueEntrySnapshot(performanceId, QueueStatus.ACTIVE, 0L, 0L, expiresAt);
    }
}
