package secondteam.iseonjwa.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.reservation.entity.ReservationSeat;
import secondteam.iseonjwa.reservation.entity.ReservationSeatId;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {

    @Query("""
            select rs from ReservationSeat rs
            join fetch rs.seat s
            where rs.reservation.reservationId in :ids
            order by s.section asc, length(s.rowNum) asc, s.rowNum asc, s.seatNum asc
            """)
    List<ReservationSeat> findAllByReservationIds(@Param("ids") List<Long> ids);
}
