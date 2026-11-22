package secondteam.iseonjwa.performance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import secondteam.iseonjwa.performance.entity.Price;
import secondteam.iseonjwa.performance.entity.PriceId;

import java.util.List;

public interface PriceRepository extends JpaRepository<Price, PriceId> {

    /** 특정 공연의 구역별 가격 목록을 모두 조회한다. */
    List<Price> findByPerformanceId(Long performanceId);
}
