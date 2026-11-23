package secondteam.iseonjwa.seat.repository;

import secondteam.iseonjwa.seat.entity.SeatSection;
import secondteam.iseonjwa.seat.entity.SeatStatus;

import java.time.LocalDateTime;

public record SeatLayoutRow(
        Long seatId,
        SeatSection section,
        String rowNum,
        Integer seatNum,
        SeatStatus status,
        LocalDateTime heldUntil,
        Long heldByMemberId
) {
}
