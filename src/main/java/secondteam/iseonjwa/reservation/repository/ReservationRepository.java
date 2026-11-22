package secondteam.iseonjwa.reservation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query(value = """
            select r from Reservation r
            join fetch r.schedule s
            join fetch s.performance
            where r.member.memberId = :memberId
              and r.status in (secondteam.iseonjwa.reservation.entity.ReservationStatus.CONFIRMED,
                               secondteam.iseonjwa.reservation.entity.ReservationStatus.CANCELED)
            """, countQuery = """
            select count(r) from Reservation r
            where r.member.memberId = :memberId
              and r.status in (secondteam.iseonjwa.reservation.entity.ReservationStatus.CONFIRMED,
                               secondteam.iseonjwa.reservation.entity.ReservationStatus.CANCELED)
            """)
    Page<Reservation> findMine(@Param("memberId") Long memberId, Pageable pageable);
}
