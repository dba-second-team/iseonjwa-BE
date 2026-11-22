package secondteam.iseonjwa.queue.dto;

import jakarta.validation.constraints.NotNull;

public record WaitingQueueRegisterRequest(
        @NotNull Long performanceId
) {
}
