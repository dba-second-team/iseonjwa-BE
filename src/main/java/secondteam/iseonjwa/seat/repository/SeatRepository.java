package secondteam.iseonjwa.seat.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.seat.entity.Seat;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

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
}
