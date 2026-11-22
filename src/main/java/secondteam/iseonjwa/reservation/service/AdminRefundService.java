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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRefundService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

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

        // 좌석은 예매 취소 시 반환되므로 관리자 환불은 결제 정보만 갱신한다.
        var paymentsByReservation = payments.stream().collect(Collectors.toMap(
                payment -> payment.getReservation().getReservationId(), payment -> payment));
        ZonedDateTime refundedAt = ZonedDateTime.now(SEOUL).withNano(0);
        payments.forEach(payment -> payment.completeRefund(refundedAt.toLocalDateTime()));

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
