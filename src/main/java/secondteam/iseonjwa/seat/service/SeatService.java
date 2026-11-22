package secondteam.iseonjwa.seat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.entity.Schedule;
import secondteam.iseonjwa.performance.repository.ScheduleRepository;
import secondteam.iseonjwa.reservation.repository.ReservationSeatRepository;
import secondteam.iseonjwa.seat.dto.SeatHoldResponse;
import secondteam.iseonjwa.seat.entity.Seat;
import secondteam.iseonjwa.seat.repository.SeatRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {
    private static final int HOLD_MINUTES = 5;
    private static final ZoneId SEAT_TIME_ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SeatHoldResponse holdSeats(long memberId, long scheduleId, List<Long> seatIds) {
        if (memberId <= 0 || scheduleId <= 0 || new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 예매 확정과 잠금 순서를 맞추고, 회원 존재 여부는 좌석 상태 확인 뒤 판정한다.
        var lockedMember = memberRepository.findByMemberId(memberId);
        Schedule schedule = scheduleRepository.findByIdWithPerformance(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));

        List<Seat> seats = new ArrayList<>(seatIds.size());
        for (Long seatId : seatIds.stream().sorted().toList()) {
            Seat seat = seatRepository.findByIdForUpdate(seatId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
            if (seat.getSchedule().getScheduleId() != scheduleId) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            seats.add(seat);
        }

        LocalDateTime now = LocalDateTime.now(SEAT_TIME_ZONE).truncatedTo(ChronoUnit.SECONDS);
        if (seats.stream().anyMatch(seat -> !seat.canBeHeldAt(now))) {
            throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE);
        }

        Member member = lockedMember.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        int limit = schedule.getPerformance().getMaxTicketsPerMember();
        long heldCount = seatRepository.countActiveHolds(memberId, scheduleId, now);
        long confirmedCount = reservationSeatRepository.countConfirmedSeats(memberId, scheduleId);
        if (heldCount + confirmedCount + seats.size() > limit) {
            throw new BusinessException(ErrorCode.HOLD_LIMIT_EXCEEDED);
        }

        LocalDateTime heldUntil = now.plusMinutes(HOLD_MINUTES);
        seats.forEach(seat -> seat.hold(member, heldUntil));
        return new SeatHoldResponse(List.copyOf(seatIds), heldUntil);
    }
}
