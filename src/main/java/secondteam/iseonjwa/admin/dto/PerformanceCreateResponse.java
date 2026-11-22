package secondteam.iseonjwa.admin.dto;

import secondteam.iseonjwa.performance.entity.PerformanceStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record PerformanceCreateResponse(
        Long performanceId,
        PerformanceStatus status,
        List<ScheduleResponse> schedules
) {
    public record ScheduleResponse(Long scheduleId, OffsetDateTime startAt) {
    }
}
