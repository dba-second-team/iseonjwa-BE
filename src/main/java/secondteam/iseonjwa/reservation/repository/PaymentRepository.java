package secondteam.iseonjwa.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import secondteam.iseonjwa.reservation.entity.Payment;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByReservationReservationIdIn(List<Long> reservationIds);
}
