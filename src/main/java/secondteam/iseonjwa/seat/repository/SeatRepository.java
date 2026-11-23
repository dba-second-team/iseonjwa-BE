package secondteam.iseonjwa.seat.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.seat.entity.Seat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /** 요청한 좌석들을 seat_id 오름차순으로 FOR UPDATE 잠그고 조회한다. (교착 방지) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Seat s
            where s.seatId in :seatIds
            order by s.seatId asc
            """)
    List<Seat> findAllByIdInForUpdate(@Param("seatIds") List<Long> seatIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.seatId = :seatId")
    Optional<Seat> findByIdForUpdate(@Param("seatId") Long seatId);

    @Query("""
            select count(s) from Seat s
            where s.heldBy.memberId = :memberId and s.schedule.scheduleId = :scheduleId
                and s.status = secondteam.iseonjwa.seat.entity.SeatStatus.HELD and s.heldUntil > :now
            """)
    long countActiveHolds(@Param("memberId") Long memberId, @Param("scheduleId") Long scheduleId,
                          @Param("now") LocalDateTime now);

    @Query("""
            select new secondteam.iseonjwa.seat.repository.SeatLayoutRow(
                s.seatId, s.section, s.rowNum, s.seatNum, s.status, s.heldUntil, m.memberId)
            from Seat s
            left join s.heldBy m
            where s.schedule.scheduleId = :scheduleId
            """)
    List<SeatLayoutRow> findLayoutByScheduleId(@Param("scheduleId") Long scheduleId);
}
