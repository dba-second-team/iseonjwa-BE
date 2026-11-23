package secondteam.iseonjwa.reservation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.reservation.entity.Reservation;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query(value = """
            select r.reservationId as reservationId,
                   s.scheduleId as scheduleId,
                   (select count(earlier)
                    from Schedule earlier
                    where earlier.performance.performanceId = p.performanceId
                      and earlier.startAt <= s.startAt) as scheduleNumber,
                   p.title as performanceTitle,
                   p.posterKey as posterKey,
                   p.venueName as venueName,
                   s.startAt as startAt,
                   r.reservedAt as reservedAt
            from Reservation r
            join r.schedule s
            join s.performance p
            where r.member.memberId = :memberId
              and r.status in (secondteam.iseonjwa.reservation.entity.ReservationStatus.CONFIRMED,
                               secondteam.iseonjwa.reservation.entity.ReservationStatus.CANCELED)
            order by r.reservedAt desc, r.reservationId desc
            """, countQuery = """
            select count(r) from Reservation r
            where r.member.memberId = :memberId
              and r.status in (secondteam.iseonjwa.reservation.entity.ReservationStatus.CONFIRMED,
                               secondteam.iseonjwa.reservation.entity.ReservationStatus.CANCELED)
            """)
    Page<MyReservationRow> findMine(@Param("memberId") Long memberId, Pageable pageable);

    /** 취소처럼 예매를 바꾸는 작업용: 예매 1행을 FOR UPDATE로 잠그고 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reservationId = :reservationId")
    Optional<Reservation> findByIdForUpdate(@Param("reservationId") Long reservationId);

    interface MyReservationRow {
        Long getReservationId();
        Long getScheduleId();
        long getScheduleNumber();
        String getPerformanceTitle();
        String getPosterKey();
        String getVenueName();
        LocalDateTime getStartAt();
        LocalDateTime getReservedAt();

    }
}
