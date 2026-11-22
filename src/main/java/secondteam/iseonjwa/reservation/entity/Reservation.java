package secondteam.iseonjwa.reservation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.performance.entity.Schedule;

@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    /** 예매를 취소 상태로 바꾸고 취소 시각을 기록한다. */
    public void cancel(LocalDateTime canceledAt) {
        this.status = ReservationStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    @Builder
    private Reservation(Member member, Schedule schedule) {
        this.member = member;
        this.schedule = schedule;
        this.status = ReservationStatus.CONFIRMED;
        this.reservedAt = LocalDateTime.now();
    }
}