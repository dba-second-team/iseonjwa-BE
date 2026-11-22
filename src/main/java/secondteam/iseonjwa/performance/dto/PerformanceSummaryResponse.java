package secondteam.iseonjwa.performance.dto;

import secondteam.iseonjwa.performance.entity.Genre;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;

import java.time.LocalDate;
import java.util.List;

public record PerformanceSummaryResponse(
        List<Item> items,
        int page,
        int size,
        long totalCount
) {
    public record Item(
            Long performanceId,
            String title,
            Genre genre,
            String posterUrl,
            PerformanceStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Integer minPrice,
            Integer maxPrice
    ) {
    }
}