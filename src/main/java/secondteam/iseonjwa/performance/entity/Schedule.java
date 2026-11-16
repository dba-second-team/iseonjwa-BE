package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule", uniqueConstraints = @UniqueConstraint(
        name = "AK_SCHEDULE_START", columnNames = {"performance_id", "start_at"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false, foreignKey = @ForeignKey(name = "R_8"))
    private Performance performance;

    @Builder
    public Schedule(Long scheduleId, LocalDateTime startAt, String status, Performance performance) {
        this.scheduleId = scheduleId;
        this.startAt = startAt;
        this.status = status;
        this.performance = performance;
    }
}
