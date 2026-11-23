package secondteam.iseonjwa.seat.dto;

import java.util.List;

public record SeatLayoutResponse(Long scheduleId, List<SeatLayoutItem> seats) {
}
