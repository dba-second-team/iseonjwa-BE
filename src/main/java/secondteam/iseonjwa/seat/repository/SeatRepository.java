package secondteam.iseonjwa.seat.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.seat.entity.Seat;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /** 요청한 좌석들을 seat_id 오름차순으로 FOR UPDATE 잠그고 조회한다. (교착 방지) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Seat s
            where s.seatId in :seatIds
            order by s.seatId asc
            """)
    List<Seat> findAllByIdInForUpdate(@Param("seatIds") List<Long> seatIds);
}
