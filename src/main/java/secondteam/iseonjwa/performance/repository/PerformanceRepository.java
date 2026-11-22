package secondteam.iseonjwa.performance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import secondteam.iseonjwa.performance.entity.Performance;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {
}
