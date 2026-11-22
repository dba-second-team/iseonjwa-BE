package secondteam.iseonjwa.queue.dto;

import jakarta.validation.constraints.NotNull;

public record ActiveQueueEnterRequest(
        @NotNull Long performanceId
) {
}
