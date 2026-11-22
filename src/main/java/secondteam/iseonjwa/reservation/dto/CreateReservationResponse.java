package secondteam.iseonjwa.reservation.dto;

import secondteam.iseonjwa.reservation.entity.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record CreateReservationResponse(
        Long reservationId,
        Long scheduleId,
        ReservationStatus status,
        List<SeatPriceInfo> seats,
        int totalAmount,
        OffsetDateTime paidAt
) {

    public record SeatPriceInfo(Long seatId, String section, int price) {
    }
}
