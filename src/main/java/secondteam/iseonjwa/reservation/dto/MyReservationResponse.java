package secondteam.iseonjwa.reservation.dto;

import secondteam.iseonjwa.reservation.entity.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record MyReservationResponse(List<Item> items, int page, int size, long totalCount) {

    public record Item(
            Long reservationId,
            Long scheduleId,
            long scheduleNumber,
            String performanceTitle,
            String venueName,
            OffsetDateTime startAt,
            List<SeatInfo> seats,
            int totalAmount,
            PaymentStatus paymentStatus,
            OffsetDateTime reservedAt
    ) {
    }

    public record SeatInfo(String section, String row, int number) {
    }
}

