package secondteam.iseonjwa.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.reservation.dto.CancelReservationResponse;
import secondteam.iseonjwa.reservation.entity.Payment;
import secondteam.iseonjwa.reservation.entity.Reservation;
import secondteam.iseonjwa.reservation.entity.ReservationStatus;
import secondteam.iseonjwa.reservation.repository.PaymentRepository;
import secondteam.iseonjwa.reservation.repository.ReservationRepository;
import secondteam.iseonjwa.reservation.repository.ReservationSeatRepository;
import secondteam.iseonjwa.seat.entity.Seat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 예매 취소 서비스 (API-5: 예매 취소 및 환불).
 * <p>
 * 환불은 두 단계로 나뉜다.
 * <ol>
 *   <li>사용자가 취소하면 예매는 CANCELED, 결제는 CANCELED("환불 진행 중")가 된다. (이 서비스)</li>
 *   <li>관리자가 환불을 처리하면 결제가 REFUNDED가 된다. (관리자 기능, 별도 구현)</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ReservationCancelService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 예매를 취소하고 수수료를 계산해 기록한 뒤, 좌석을 반환한다. (한 트랜잭션)
     *
     * @param memberId      요청한 회원 ID (헤더 X-Member-Id)
     * @param reservationId 취소할 예매 ID
     * @throws BusinessException 회원이 없으면 MEMBER_NOT_FOUND, 예매가 없으면 RESERVATION_NOT_FOUND,
     *                           내 예매가 아니면 FORBIDDEN, 이미 취소됐으면 ALREADY_CANCELED,
     *                           공연 당일이거나 지났으면 CANCEL_NOT_ALLOWED
     */
    @Transactional
    public CancelReservationResponse cancel(Long memberId, Long reservationId) {
        // 1. 회원 확인
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        // 2. 예매 1행을 FOR UPDATE로 잠그고 조회한다. (같은 예매를 동시에 두 번 취소하지 못하게 한다)
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        // 3. 내 예매인지, 아직 취소 가능한 상태인지 확인한다.
        if (!reservation.getMember().getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED);
        }

        // 4. 공연 날짜까지 남은 일수(날짜 기준, 한국 시간)로 취소 가능 여부와 수수료를 정한다.
        LocalDateTime now = LocalDateTime.now(ZONE);
        long daysUntil = ChronoUnit.DAYS.between(
                now.toLocalDate(), reservation.getSchedule().getStartAt().toLocalDate());
        if (!CancelFeePolicy.isCancelable(daysUntil)) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
        }

        Payment payment = paymentRepository.findByReservationReservationId(reservationId)
                .orElseThrow(() -> new IllegalStateException("결제 정보가 없는 예매입니다. reservationId=" + reservationId));
        int paidAmount = payment.getPaidAmount();
        int cancelFee = CancelFeePolicy.calculateFee(paidAmount, daysUntil);
        int refundAmount = paidAmount - cancelFee;

        // 5. 예매는 CANCELED, 결제는 "환불 진행 중"(CANCELED)으로 바꾸고 수수료/환불액을 기록한다.
        reservation.cancel(now);
        payment.cancel(cancelFee, refundAmount);

        // 6. 좌석 반환: seat_id 순서로 잠그고 AVAILABLE로 돌린다. (예매좌석 행은 내역 조회를 위해 남겨둔다)
        List<Seat> seats = reservationSeatRepository.findSeatsForUpdate(reservationId);
        seats.forEach(Seat::release);

        return new CancelReservationResponse(
                reservation.getReservationId(), reservation.getStatus(), paidAmount, cancelFee, refundAmount);
    }
}
