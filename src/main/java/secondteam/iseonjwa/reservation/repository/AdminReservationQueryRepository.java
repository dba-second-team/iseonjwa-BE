package secondteam.iseonjwa.reservation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.reservation.entity.Reservation;
import secondteam.iseonjwa.seat.entity.SeatSection;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminReservationQueryRepository extends Repository<Reservation, Long> {
    @Query(value = """
            select r.reservationId as reservationId,
                   m.name as memberName,
                   s.scheduleId as scheduleId,
                   (select count(earlier)
                    from Schedule earlier
                    where earlier.performance.performanceId = :performanceId
                      and earlier.startAt <= s.startAt) as scheduleNumber,
                   s.startAt as startAt,
                   r.reservedAt as reservedAt,
                   p.paidAmount as paidAmount,
                   p.cancelFee as cancelFee,
                   p.refundAmount as refundAmount
            from Payment p
            join p.reservation r
            join r.schedule s
            join r.member m
            where s.performance.performanceId = :performanceId and p.status = :status
            order by r.reservedAt desc, r.reservationId desc
            """, countQuery = """
            select count(p)
            from Payment p
            join p.reservation r
            join r.schedule s
            where s.performance.performanceId = :performanceId and p.status = :status
            """)
    Page<ReservationRow> findReservationPage(
            @Param("performanceId") long performanceId,
            @Param("status") PaymentStatus status,
            Pageable pageable
    );

    @Query("""
            select rs.reservation.reservationId as reservationId,
                   s.section as section, s.rowNum as rowNum, s.seatNum as seatNum
            from ReservationSeat rs
            join rs.seat s
            where rs.reservation.reservationId in :reservationIds
            order by rs.reservation.reservationId, s.section, s.rowNum, s.seatNum
            """)
    List<SeatRow> findSeats(@Param("reservationIds") List<Long> reservationIds);

    @Query("select count(p) > 0 from Performance p where p.performanceId = :performanceId")
    boolean existsPerformance(@Param("performanceId") long performanceId);

    interface ReservationRow {
        long getReservationId();
        String getMemberName();
        long getScheduleId();
        long getScheduleNumber();
        LocalDateTime getStartAt();
        LocalDateTime getReservedAt();
        int getPaidAmount();
        int getCancelFee();
        int getRefundAmount();
    }

    interface SeatRow {
        long getReservationId();
        SeatSection getSection();
        String getRowNum();
        int getSeatNum();
    }
}
