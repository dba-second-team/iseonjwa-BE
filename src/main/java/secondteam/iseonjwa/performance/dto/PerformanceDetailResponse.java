package secondteam.iseonjwa.performance.dto;

import secondteam.iseonjwa.performance.entity.Genre;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;

import java.time.LocalDate;

public record PerformanceDetailResponse(
        Long performanceId,
        String title,
        Genre genre,
        String posterUrl,
        PerformanceStatus status,
        String venueName,
        Integer runningTime,
        String description,
        Integer maxTicketsPerMember,
        LocalDate startDate,
        LocalDate endDate
) {
}