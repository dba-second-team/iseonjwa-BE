package secondteam.iseonjwa.performance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import secondteam.iseonjwa.performance.entity.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}
