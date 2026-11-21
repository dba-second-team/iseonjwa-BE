package secondteam.iseonjwa.reservation.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.reservation.entity.Payment;
import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.seat.entity.Seat;

import java.util.Collection;
import java.util.List;

public interface AdminRefundRepository extends Repository<Payment, Long> {
    @Query("select count(p) > 0 from Performance p where p.performanceId = :performanceId")
    boolean existsPerformance(@Param("performanceId") long performanceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Payment p
            where p.reservation.reservationId in (
                select r.reservationId from Reservation r
                where r.reservationId in :reservationIds
                  and r.schedule.performance.performanceId = :performanceId
            )
            order by p.paymentId
            """)
    List<Payment> lockPayments(@Param("performanceId") long performanceId,
                               @Param("reservationIds") Collection<Long> reservationIds);

    @Query("""
            select rs.reservation.reservationId as reservationId,
                   rs.seat.seatId as seatId,
                   rs.reservation.schedule.scheduleId as reservationScheduleId,
                   rs.seat.schedule.scheduleId as seatScheduleId
            from ReservationSeat rs
            where rs.reservation.reservationId in :reservationIds
            """)
    List<SeatLink> findSeatLinks(@Param("reservationIds") Collection<Long> reservationIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.seatId in :seatIds order by s.seatId")
    List<Seat> lockSeats(@Param("seatIds") Collection<Long> seatIds);

    @Query("""
            select count(link) > 0 from ReservationSeat link
            join Payment p on p.reservation = link.reservation
            where link.seat.seatId in :seatIds
              and link.reservation.reservationId not in :reservationIds
              and p.status in :occupiedPaymentStatuses
            """)
    boolean hasOtherSeatOwners(@Param("seatIds") Collection<Long> seatIds,
                               @Param("reservationIds") Collection<Long> reservationIds,
                               @Param("occupiedPaymentStatuses") Collection<PaymentStatus> occupiedPaymentStatuses);

    interface SeatLink {
        long getReservationId();
        long getSeatId();
        long getReservationScheduleId();
        long getSeatScheduleId();
    }
}
