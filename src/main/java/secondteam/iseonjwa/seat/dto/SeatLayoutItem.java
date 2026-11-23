package secondteam.iseonjwa.seat.dto;

import secondteam.iseonjwa.seat.entity.SeatSection;
import secondteam.iseonjwa.seat.entity.SeatStatus;

public record SeatLayoutItem(
        Long seatId,
        SeatSection section,
        String rowNum,
        Integer seatNum,
        SeatStatus status,
        Integer price,
        boolean heldByMe
) {
}
