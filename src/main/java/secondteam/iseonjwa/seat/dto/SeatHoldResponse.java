package secondteam.iseonjwa.seat.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SeatHoldResponse(List<Long> seatIds, LocalDateTime heldUntil) {
}
