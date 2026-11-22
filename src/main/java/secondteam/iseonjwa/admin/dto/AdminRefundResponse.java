package secondteam.iseonjwa.admin.dto;

import secondteam.iseonjwa.reservation.entity.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminRefundResponse(List<Item> items) {
    public record Item(long reservationId, PaymentStatus status, int refundAmount, OffsetDateTime refundedAt) {}
}
