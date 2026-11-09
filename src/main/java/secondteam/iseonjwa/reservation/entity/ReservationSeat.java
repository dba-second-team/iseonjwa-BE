package secondteam.iseonjwa.reservation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.seat.entity.ScheduledSeat;

@Entity
@Table(name = "RESERVATION_SEAT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_seat_id")
    private Long reservationSeatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheduled_seat_id", nullable = false)
    private ScheduledSeat scheduledSeat;

    /** Price snapshot at the time of booking. */
    @Column(name = "price", nullable = false)
    private Integer price;

    @Builder
    private ReservationSeat(Reservation reservation, ScheduledSeat scheduledSeat, Integer price) {
        this.reservation = reservation;
        this.scheduledSeat = scheduledSeat;
        this.price = price;
    }
}
