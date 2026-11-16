package secondteam.iseonjwa.reservation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.seat.entity.Seat;

@Entity
@Table(name = "reservation_seat")
@IdClass(ReservationSeatId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", foreignKey = @ForeignKey(name = "fk_reservation_seat_reservation"))
    private Reservation reservation;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", foreignKey = @ForeignKey(name = "fk_reservation_seat_seat"))
    private Seat seat;

    @Builder
    private ReservationSeat(Reservation reservation, Seat seat) {
        this.reservation = reservation;
        this.seat = seat;
    }
}
