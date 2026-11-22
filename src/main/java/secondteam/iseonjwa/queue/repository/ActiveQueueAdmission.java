package secondteam.iseonjwa.queue.repository;

import java.time.Instant;

/**
 * 예매 작업열(active_queue) 진입 결과 스냅샷.
 *
 * @param admittedAt 작업열 진입 허가 시각
 * @param expiresAt  작업열 만료 시각
 */
public record ActiveQueueAdmission(
        Instant admittedAt,
        Instant expiresAt
) {
}
