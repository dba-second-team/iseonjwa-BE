package secondteam.iseonjwa.reservation.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.reservation.entity.Payment;

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
}
