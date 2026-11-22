package secondteam.iseonjwa.queue.dto;

import java.time.OffsetDateTime;

public record QueueStatusResponse(
        Long performanceId,
        QueueStatus status,
        long position,
        boolean canEnter,
        OffsetDateTime expiresAt
) {
}
