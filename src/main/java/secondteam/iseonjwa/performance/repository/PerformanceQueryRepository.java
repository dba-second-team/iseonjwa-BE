package secondteam.iseonjwa.performance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;
import secondteam.iseonjwa.performance.entity.Schedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PerformanceQueryRepository extends Repository<Performance, Long> {

    Page<Performance> findAllByStatusNot(PerformanceStatus status, Pageable pageable);

    Optional<Performance> findByPerformanceId(Long performanceId);

    boolean existsByPerformanceId(Long performanceId);

    @Query("""
            select schedule
            from Schedule schedule
            where schedule.performance.performanceId = :performanceId
            order by schedule.startAt asc
            """)
    List<Schedule> findSchedules(@Param("performanceId") Long performanceId);

    @Query("""
            select schedule.performance.performanceId as performanceId,
                   min(schedule.startAt) as firstStartAt,
                   max(schedule.startAt) as lastStartAt
            from Schedule schedule
            where schedule.performance.performanceId in :performanceIds
            group by schedule.performance.performanceId
            """)
    List<SchedulePeriod> findSchedulePeriods(@Param("performanceIds") List<Long> performanceIds);

    @Query("""
            select price.performanceId as performanceId,
                   min(price.price) as minPrice,
                   max(price.price) as maxPrice
            from Price price
            where price.performanceId in :performanceIds
            group by price.performanceId
            """)
    List<PriceRange> findPriceRanges(@Param("performanceIds") List<Long> performanceIds);

    interface SchedulePeriod {
        Long getPerformanceId();

        LocalDateTime getFirstStartAt();

        LocalDateTime getLastStartAt();
    }

    interface PriceRange {
        Long getPerformanceId();

        Integer getMinPrice();

        Integer getMaxPrice();
    }
}