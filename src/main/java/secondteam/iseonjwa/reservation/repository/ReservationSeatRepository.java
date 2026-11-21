package secondteam.iseonjwa.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.reservation.entity.ReservationSeat;
import secondteam.iseonjwa.reservation.entity.ReservationSeatId;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {

    @Query("""
            select count(distinct rs.seat.seatId) from ReservationSeat rs
            join rs.reservation r
            where r.member.memberId = :memberId and r.schedule.scheduleId = :scheduleId
                and r.status = secondteam.iseonjwa.reservation.entity.ReservationStatus.CONFIRMED
            """)
    long countConfirmedSeats(@Param("memberId") Long memberId, @Param("scheduleId") Long scheduleId);
}
