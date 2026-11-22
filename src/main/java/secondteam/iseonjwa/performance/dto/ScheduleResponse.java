package secondteam.iseonjwa.performance.dto;

import java.time.LocalDateTime;

public record ScheduleResponse(
        Long scheduleId,
        int round,
        LocalDateTime startAt
) {
}