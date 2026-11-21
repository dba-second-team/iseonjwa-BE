package secondteam.iseonjwa.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.admin.dto.AdminRefundResponse;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.reservation.entity.Payment;
import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.reservation.repository.AdminRefundRepository;
import secondteam.iseonjwa.reservation.repository.AdminRefundRepository.SeatLink;
import secondteam.iseonjwa.seat.entity.Seat;
import secondteam.iseonjwa.seat.entity.SeatStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRefundService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<PaymentStatus> OCCUPIED_PAYMENT_STATUSES = Set.of(
            PaymentStatus.PAID, PaymentStatus.CANCELED);

    private final AdminRefundRepository refundRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminRefundResponse complete(long performanceId, List<Long> reservationIds) {
        if (performanceId < 1 || new HashSet<>(reservationIds).size() != reservationIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!refundRepository.existsPerformance(performanceId)) {
            throw new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND);
        }

        List<Payment> payments = refundRepository.lockPayments(performanceId, reservationIds);
        if (payments.size() != reservationIds.size()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        for (Payment payment : payments) {
            if (payment.getStatus() != PaymentStatus.CANCELED) {
                throw new BusinessException(ErrorCode.REFUND_NOT_PENDING);
            }
            if (payment.getCancelFee() < 0 || payment.getCancelFee() > payment.getPaidAmount()) {
                throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT);
            }
        }

        List<SeatLink> seatLinks = refundRepository.findSeatLinks(reservationIds);
        Set<Long> reservationsWithSeats = seatLinks.stream()
                .map(SeatLink::getReservationId)
                .collect(Collectors.toSet());
        Set<Long> seatIds = seatLinks.stream().map(SeatLink::getSeatId).collect(Collectors.toSet());
        if (reservationsWithSeats.size() != reservationIds.size() || seatIds.size() != seatLinks.size()
                || seatLinks.stream().anyMatch(link -> link.getReservationScheduleId() != link.getSeatScheduleId())) {
            throw new BusinessException(ErrorCode.REFUND_SEAT_CONFLICT);
        }

        List<Seat> seats = refundRepository.lockSeats(seatIds);
        // 좌석 잠금을 획득한 뒤 현재 점유 관계를 확인한다. 환불 이력이 있는 좌석 연결은 보존된다.
        if (seats.size() != seatIds.size()
                || seats.stream().anyMatch(seat -> seat.getStatus() != SeatStatus.RESERVED)
                || refundRepository.hasOtherSeatOwners(seatIds, reservationIds, OCCUPIED_PAYMENT_STATUSES)) {
            throw new BusinessException(ErrorCode.REFUND_SEAT_CONFLICT);
        }

        var paymentsByReservation = payments.stream().collect(Collectors.toMap(
                payment -> payment.getReservation().getReservationId(), payment -> payment));
        ZonedDateTime refundedAt = ZonedDateTime.now(SEOUL).withNano(0);
        payments.forEach(payment -> payment.completeRefund(refundedAt.toLocalDateTime()));
        seats.forEach(Seat::releaseAfterRefund);

        List<AdminRefundResponse.Item> items = reservationIds.stream()
                .map(reservationId -> {
                    Payment payment = paymentsByReservation.get(reservationId);
                    return new AdminRefundResponse.Item(reservationId, payment.getStatus(),
                            payment.getRefundAmount(), refundedAt.toOffsetDateTime());
                })
                .toList();
        return new AdminRefundResponse(items);
    }
}
