package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "SCHEDULE", uniqueConstraints = {
    @UniqueConstraint(name = "AK_SCHEDULE_START", columnNames = {"performance_id", "start_at"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Builder
    private Schedule(Performance performance, LocalDateTime startAt, String status) {
        this.performance = performance;
        this.startAt = startAt;
        this.status = status;
    }
}
