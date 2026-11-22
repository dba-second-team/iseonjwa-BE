package secondteam.iseonjwa.queue.dto;

import java.time.OffsetDateTime;

public record WaitingQueueRegisterResponse(
        Long performanceId,
        QueueStatus status,
        long position,
        OffsetDateTime enteredAt
) {
}
