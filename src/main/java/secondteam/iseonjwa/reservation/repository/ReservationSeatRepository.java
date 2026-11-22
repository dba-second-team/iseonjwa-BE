package secondteam.iseonjwa.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.reservation.entity.ReservationSeat;
import secondteam.iseonjwa.reservation.entity.ReservationSeatId;
import secondteam.iseonjwa.seat.entity.Seat;

import jakarta.persistence.LockModeType;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {

    @Query("""
            select rs from ReservationSeat rs
            join fetch rs.seat s
            where rs.reservation.reservationId in :ids
            order by s.section asc, length(s.rowNum) asc, s.rowNum asc, s.seatNum asc
            """)
    List<ReservationSeat> findAllByReservationIds(@Param("ids") List<Long> ids);

    /** 취소할 때 반환할 좌석을 seat_id 순서로 FOR UPDATE 잠그고 조회한다. (잠금 순서를 고정해 교착을 줄인다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from ReservationSeat rs
            join rs.seat s
            where rs.reservation.reservationId = :reservationId
            order by s.seatId asc
            """)
    List<Seat> findSeatsForUpdate(@Param("reservationId") Long reservationId);
}
