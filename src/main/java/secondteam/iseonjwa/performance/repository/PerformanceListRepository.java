package secondteam.iseonjwa.performance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.reservation.entity.ReservationStatus;

import java.util.List;

public interface PerformanceListRepository extends Repository<Performance, Long> {
    Page<Performance> findAllBy(Pageable pageable);

    @Query("""
            select schedule.performance.performanceId as performanceId, count(reservationSeat.seat.seatId) as soldTicketCount
            from ReservationSeat reservationSeat
            join reservationSeat.reservation reservation
            join reservation.schedule schedule
            join Payment payment on payment.reservation = reservation
            where schedule.performance.performanceId in :performanceIds
              and reservation.status = :reservationStatus
              and payment.status = :paymentStatus
            group by schedule.performance.performanceId
            """)
    List<TicketCount> countSoldTickets(
            @Param("performanceIds") List<Long> performanceIds,
            @Param("reservationStatus") ReservationStatus reservationStatus,
            @Param("paymentStatus") PaymentStatus paymentStatus
    );

    interface TicketCount {
        Long getPerformanceId();

        long getSoldTicketCount();
    }
}
