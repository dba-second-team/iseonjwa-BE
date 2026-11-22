package secondteam.iseonjwa.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import secondteam.iseonjwa.performance.entity.Genre;
import secondteam.iseonjwa.seat.entity.SeatSection;

import java.time.OffsetDateTime;
import java.util.List;

public record PerformanceCreateRequest(
        @NotBlank @Size(max = 18) String title,
        @NotNull Genre genre,
        @NotBlank @Size(max = 200) String venueName,
        @Size(max = 1500) String description,
        @NotNull @Positive Integer runningTime,
        @NotNull @Min(1) Integer maxTicketsPerMember,
        @NotNull @Size(min = 1, max = 4) List<@NotNull @Valid ScheduleRequest> schedules,
        @NotNull @Size(min = 7, max = 7) List<@NotNull @Valid SectionPriceRequest> prices
) {
    public record ScheduleRequest(@NotNull OffsetDateTime startAt) {
    }

    public record SectionPriceRequest(@NotNull SeatSection section, @NotNull @Min(0) Integer price) {
    }
}
