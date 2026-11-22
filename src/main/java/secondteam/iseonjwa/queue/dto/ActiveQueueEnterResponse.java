package secondteam.iseonjwa.queue.dto;

import java.time.OffsetDateTime;

public record ActiveQueueEnterResponse(
        Long performanceId,
        QueueStatus status,
        OffsetDateTime admittedAt,
        OffsetDateTime expiresAt
) {
}
