package secondteam.iseonjwa.reservation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.performance.entity.Schedule;
import secondteam.iseonjwa.seat.entity.Seat;

@Entity
@Table(name = "reservation")
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

    protected Reservation() {}

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long value) { this.reservationId = value; }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus value) { this.status = value; }

    public LocalDateTime getReservedAt() { return reservedAt; }
    public void setReservedAt(LocalDateTime value) { this.reservedAt = value; }

    public LocalDateTime getCanceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime value) { this.canceledAt = value; }

    public Member getMember() { return member; }
    public void setMember(Member value) { this.member = value; }

    public Schedule getSchedule() { return schedule; }
    public void setSchedule(Schedule value) { this.schedule = value; }

    public Seat getSeat() { return seat; }
    public void setSeat(Seat value) { this.seat = value; }
}
