package secondteam.iseonjwa.admin.dto;

import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.seat.entity.SeatSection;

import java.time.OffsetDateTime;
import java.util.List;

public record PerformanceReservationListResponse(List<Item> items, int page, int size, long totalCount) {
    public record Item(
            long reservationId,
            String memberName,
            long scheduleId,
            long scheduleNumber,
            OffsetDateTime startAt,
            OffsetDateTime reservedAt,
            List<SeatItem> seats,
            int ticketCount,
            int paidAmount,
            PaymentStatus status,
            Integer expectedRefundAmount,
            Integer refundAmount
    ) {}

    public record SeatItem(SeatSection section, String rowNum, int seatNum) {}
}
