package secondteam.iseonjwa.reservation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.performance.entity.Schedule;
import secondteam.iseonjwa.seat.entity.Seat;

@Entity
@Table(name = "reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "R_12"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, foreignKey = @ForeignKey(name = "R_13"))
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false, foreignKey = @ForeignKey(name = "R_19"))
    private Seat seat;

    @Builder
    public Reservation(Long reservationId, ReservationStatus status, LocalDateTime reservedAt,
                       LocalDateTime canceledAt, Member member, Schedule schedule, Seat seat) {
        this.reservationId = reservationId;
        this.status = status;
        this.reservedAt = reservedAt;
        this.canceledAt = canceledAt;
        this.member = member;
        this.schedule = schedule;
        this.seat = seat;
    }
}
