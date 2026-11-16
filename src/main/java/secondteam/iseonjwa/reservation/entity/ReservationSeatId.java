package secondteam.iseonjwa.reservation.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReservationSeatId implements Serializable {

    private Long reservation;
    private Long seat;
}
