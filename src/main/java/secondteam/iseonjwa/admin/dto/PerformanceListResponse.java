package secondteam.iseonjwa.admin.dto;

import secondteam.iseonjwa.performance.entity.Genre;

import java.util.List;

public record PerformanceListResponse(
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
            long soldTicketCount
    ) {
    }
}
