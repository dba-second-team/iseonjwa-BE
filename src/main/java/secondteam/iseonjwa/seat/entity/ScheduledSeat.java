package secondteam.iseonjwa.seat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.performance.entity.Schedule;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_seat", uniqueConstraints = {
    @UniqueConstraint(name = "AK_SCHEDULED_SEAT", columnNames = {"schedule_id", "performance_seat_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scheduled_seat_id")
    private Long scheduledSeatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_seat_id", nullable = false)
    private PerformanceSeat performanceSeat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduledSeatStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "held_by")
    private Member heldBy;

    @Column(name = "held_until")
    private LocalDateTime heldUntil;

    @Builder
    private ScheduledSeat(Schedule schedule, PerformanceSeat performanceSeat, ScheduledSeatStatus status,
                          Member heldBy, LocalDateTime heldUntil) {
        this.schedule = schedule;
        this.performanceSeat = performanceSeat;
        this.status = status;
        this.heldBy = heldBy;
        this.heldUntil = heldUntil;
    }
}
