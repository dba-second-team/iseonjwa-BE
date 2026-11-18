package secondteam.iseonjwa.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.reservation.dto.MyReservationResponse;
import secondteam.iseonjwa.reservation.entity.Payment;
import secondteam.iseonjwa.reservation.entity.Reservation;
import secondteam.iseonjwa.reservation.entity.ReservationStatus;
import secondteam.iseonjwa.reservation.repository.PaymentRepository;
import secondteam.iseonjwa.reservation.repository.ReservationRepository;
import secondteam.iseonjwa.reservation.repository.ReservationSeatRepository;
import secondteam.iseonjwa.seat.entity.Seat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 예매 "조회" 전용 서비스 (API-4: 내 예매 내역).
 * <p>
 * 데이터를 바꾸지 않으므로 클래스 전체에 읽기 전용 트랜잭션을 적용한다.
 * 예매 확정/취소처럼 데이터를 변경하는 로직은 이 클래스에 넣지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationQueryService {

    /** DB의 LocalDateTime(시간대 없음)을 응답용 +09:00 시각으로 바꿀 때 쓰는 기준 시간대 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 헤더의 회원이 한 예매 목록을 최신 예매순으로 조회한다.
     *
     * @param memberId 요청한 회원 ID (헤더 X-Member-Id)
     * @param status   조회할 예매 상태. null이면 CONFIRMED, CANCELED 모두 조회
     * @param page     페이지 번호 (0부터 시작)
     * @param size     한 페이지에 보여줄 개수
     * @throws BusinessException page/size가 잘못되면 INVALID_REQUEST, 회원이 없으면 MEMBER_NOT_FOUND
     */
    public MyReservationResponse getMyReservations(Long memberId, ReservationStatus status, int page, int size) {
        // 1. 요청값 검증: 음수 페이지, 0 이하의 size는 허용하지 않는다.
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // 2. 회원 존재 확인: 없는 회원이면 빈 목록 대신 404로 알려준다.
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        // 3. 조회할 상태 결정: 필터가 없으면 확정/취소 예매를 모두 보여준다. (PENDING은 제외)
        List<ReservationStatus> statuses = (status == null)
                ? List.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED)
                : List.of(status);

        // 4. 예매 목록 조회 (예매 + 회차 + 공연을 한 번에 가져온다), 최신 예매순 정렬
        Page<Reservation> result = reservationRepository.findMine(
                memberId, statuses,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reservedAt")));

        // 5. 이번 페이지 예매들의 ID 모음 (아래 좌석/결제를 한 번에 조회하기 위함)
        List<Long> ids = result.getContent().stream().map(Reservation::getReservationId).toList();

        // 6. 좌석 조회: 예매 ID별로 좌석 목록을 묶는다. (예매마다 따로 조회하면 쿼리가 많아지는 N+1을 피한다)
        //    좌석은 한 예매에 여러 개라서 페이징 쿼리와 합치지 않고 따로 가져온다.
        Map<Long, List<MyReservationResponse.SeatInfo>> seatsByReservation = ids.isEmpty() ? Map.of()
                : reservationSeatRepository.findAllByReservationIds(ids).stream()
                .collect(Collectors.groupingBy(
                        rs -> rs.getReservation().getReservationId(),
                        Collectors.mapping(rs -> toSeatInfo(rs.getSeat()), Collectors.toList())));

        // 7. 결제 금액 조회: 예매 ID -> 결제 금액(totalAmount). 예매 1건당 결제는 1건이다.
        Map<Long, Integer> amountByReservation = ids.isEmpty() ? Map.of()
                : paymentRepository.findByReservationReservationIdIn(ids).stream()
                .collect(Collectors.toMap(
                        p -> p.getReservation().getReservationId(),
                        Payment::getPaidAmount));

        // 8. 응답 항목 조립: 예매 + 공연 정보 + 좌석 목록 + 결제 금액
        //    공연 정보는 예매 -> 회차(schedule) -> 공연(performance)을 따라가서 꺼낸다.
        List<MyReservationResponse.Item> items = result.getContent().stream()
                .map(r -> new MyReservationResponse.Item(
                        r.getReservationId(),
                        r.getSchedule().getPerformance().getTitle(),
                        r.getSchedule().getPerformance().getVenueName(),
                        toOffset(r.getSchedule().getStartAt()),
                        seatsByReservation.getOrDefault(r.getReservationId(), List.of()),
                        amountByReservation.getOrDefault(r.getReservationId(), 0),
                        r.getStatus(),
                        toOffset(r.getReservedAt())))
                .toList();

        // 9. totalCount는 이번 페이지 개수가 아니라, 조건에 맞는 전체 예매 건수다.
        return new MyReservationResponse(items, page, size, result.getTotalElements());
    }

    /** Seat 엔티티를 응답용 좌석 정보로 변환한다. (rowNum -> row, seatNum -> number, seatRank -> grade) */
    private MyReservationResponse.SeatInfo toSeatInfo(Seat seat) {
        return new MyReservationResponse.SeatInfo(
                seat.getSection().name(),
                seat.getRowNum(),
                seat.getSeatNum(),
                seat.getSeatRank().name());
    }

    /** 시간대가 없는 LocalDateTime을 한국 시간(+09:00)이 붙은 OffsetDateTime으로 변환한다. */
    private OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE).toOffsetDateTime();
    }
}

