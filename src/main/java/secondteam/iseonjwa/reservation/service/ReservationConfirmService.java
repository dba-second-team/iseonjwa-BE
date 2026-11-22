package secondteam.iseonjwa.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.entity.Price;
import secondteam.iseonjwa.performance.entity.Schedule;
import secondteam.iseonjwa.performance.repository.PriceRepository;
import secondteam.iseonjwa.performance.repository.ScheduleRepository;
import secondteam.iseonjwa.reservation.dto.CreateReservationRequest;
import secondteam.iseonjwa.reservation.dto.CreateReservationResponse;
import secondteam.iseonjwa.reservation.entity.Payment;
import secondteam.iseonjwa.reservation.entity.Reservation;
import secondteam.iseonjwa.reservation.entity.ReservationSeat;
import secondteam.iseonjwa.reservation.repository.PaymentRepository;
import secondteam.iseonjwa.reservation.repository.ReservationRepository;
import secondteam.iseonjwa.reservation.repository.ReservationSeatRepository;
import secondteam.iseonjwa.seat.entity.Seat;
import secondteam.iseonjwa.seat.entity.SeatSection;
import secondteam.iseonjwa.seat.repository.SeatRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 결제 및 예매 확정 서비스 (8.0 결제 및 예매 확정).
 * <p>
 * 8.1 선점 좌석 잠금/검증 -> 8.2 구역별 결제 금액 계산 -> 8.3 예매 정보 저장
 * -> 8.4 결제 저장·좌석 확정·작업열 삭제 전 과정을 단일 트랜잭션으로 처리한다.
 * 중간에 하나라도 실패하면 전체 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class ReservationConfirmService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final PriceRepository priceRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 선점한 좌석들에 대해 가상 결제를 수행하고 예매를 확정한다. (단일 트랜잭션)
     *
     * @param memberId 요청한 회원 ID (헤더 X-Member-Id)
     * @param request  회차 ID, 선점 좌석 ID 목록, 가상 결제 수단
     */
    @Transactional
    public CreateReservationResponse confirm(Long memberId, CreateReservationRequest request) {
        // 0. 요청 기본값 검증: 빈 좌석 목록, null 원소, 중복 좌석 ID는 허용하지 않는다.
        validateRequest(request);

        // 1. 회원 및 회차(공연 포함) 존재 확인
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Schedule schedule = scheduleRepository.findByIdWithPerformance(request.scheduleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));

        // 2. [8.1 선점 좌석 잠금 및 검증] 교착 방지를 위해 seat_id 오름차순으로 FOR UPDATE 잠금 조회한다.
        List<Long> sortedSeatIds = request.seatIds().stream().sorted().toList();
        List<Seat> seats = seatRepository.findAllByIdInForUpdate(sortedSeatIds);

        // 없는 좌석 ID가 섞여 있거나 요청한 회차(scheduleId) 소속이 아닌 좌석이 있으면 잘못된 요청이다.
        if (seats.size() != sortedSeatIds.size()
                || seats.stream().anyMatch(seat -> !seat.getSchedule().getScheduleId().equals(request.scheduleId()))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 플로우차트 분기 1: 본인 선점 좌석인지 확인 (status == HELD && held_by == memberId)
        if (seats.stream().anyMatch(seat -> !seat.isHeldBy(memberId))) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }
        // 플로우차트 분기 2: 선점 유효 시간 확인 (held_until > now)
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (seats.stream().anyMatch(seat -> !seat.isHoldValidAt(now))) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }

        // 3. [8.2 결제 금액 계산] 공연의 구역(section)별 가격을 읽어 좌석별 금액과 총 결제 금액을 계산한다.
        Map<SeatSection, Integer> priceBySection = priceRepository
                .findByPerformanceId(schedule.getPerformance().getPerformanceId())
                .stream()
                .collect(Collectors.toMap(Price::getSection, Price::getPrice));

        List<CreateReservationResponse.SeatPriceInfo> seatPriceInfos = new ArrayList<>(seats.size());
        int totalAmount = 0;
        for (Seat seat : seats) {
            Integer price = priceBySection.get(seat.getSection());
            if (price == null) {
                throw new BusinessException(ErrorCode.PRICE_NOT_SET);
            }
            seatPriceInfos.add(new CreateReservationResponse.SeatPriceInfo(
                    seat.getSeatId(), seat.getSection().name(), price));
            totalAmount += price;
        }

        // 4. [8.3 예매 정보 저장] 예매(CONFIRMED) 1행과 예매좌석 n행을 INSERT한다.
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .member(member)
                .schedule(schedule)
                .build());

        List<ReservationSeat> reservationSeats = seats.stream()
                .map(seat -> ReservationSeat.builder()
                        .reservation(reservation)
                        .seat(seat)
                        .build())
                .toList();
        reservationSeatRepository.saveAll(reservationSeats);

        // 5. [8.4 좌석 예매 확정] 결제(PAID) 1행을 INSERT하고, 좌석을 RESERVED로 확정하며 작업열에서 회원을 제거한다.
        Payment payment = paymentRepository.save(Payment.builder()
                .reservation(reservation)
                .paidAmount(totalAmount)
                .build());

        seats.forEach(Seat::reserve);
        removeFromActiveQueue(memberId);

        return new CreateReservationResponse(
                reservation.getReservationId(),
                schedule.getScheduleId(),
                reservation.getStatus(),
                seatPriceInfos,
                totalAmount,
                toOffset(payment.getPaidAt()));
    }

    private void validateRequest(CreateReservationRequest request) {
        if (request == null
                || request.scheduleId() == null
                || request.paymentMethod() == null
                || request.seatIds() == null
                || request.seatIds().isEmpty()
                || request.seatIds().stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (new HashSet<>(request.seatIds()).size() != request.seatIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 예매 작업열(Active_queue)에서 완료된 회원 정보를 제거한다.
     * 작업열(대기열) 기능 구현 시 해당 저장소 삭제 호출을 이 위치에 연결한다.
     */
    private void removeFromActiveQueue(Long memberId) {
        // Active_queue 구현체 연동 지점 (현재 스키마/인프라 미구현 상태로 no-op 유지)
    }

    private OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE).toOffsetDateTime();
    }
}
