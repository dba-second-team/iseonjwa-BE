package secondteam.iseonjwa.reservation.dto;

import secondteam.iseonjwa.reservation.entity.ReservationStatus;

public record CancelReservationResponse(
        Long reservationId,
        ReservationStatus status,
        int paidAmount,
        int cancelFee,
        int refundAmount
) {
}
