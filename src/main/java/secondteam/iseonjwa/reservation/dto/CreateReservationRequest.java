package secondteam.iseonjwa.reservation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateReservationRequest(
        @NotNull Long scheduleId,
        @NotEmpty List<@NotNull Long> seatIds,
        @NotNull PaymentMethod paymentMethod
) {
}
