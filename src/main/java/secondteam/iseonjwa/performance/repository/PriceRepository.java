package secondteam.iseonjwa.performance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import secondteam.iseonjwa.performance.entity.Price;
import secondteam.iseonjwa.performance.entity.PriceId;

public interface PriceRepository extends JpaRepository<Price, PriceId> {
}
