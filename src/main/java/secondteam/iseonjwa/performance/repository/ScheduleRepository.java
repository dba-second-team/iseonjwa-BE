package secondteam.iseonjwa.performance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.performance.entity.Schedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    @Query("""
            select schedule
            from Schedule schedule
            join fetch schedule.performance performance
            where lower(performance.venueName) = lower(:venueName)
              and schedule.startAt < :latestEndAt
            """)
    List<Schedule> findVenueSchedulesBefore(
            @Param("venueName") String venueName,
            @Param("latestEndAt") LocalDateTime latestEndAt
    );

    /** 회차와 소속 공연 정보를 한 번에 조회한다. */
    @Query("select s from Schedule s join fetch s.performance where s.scheduleId = :scheduleId")
    Optional<Schedule> findByIdWithPerformance(@Param("scheduleId") Long scheduleId);
}
