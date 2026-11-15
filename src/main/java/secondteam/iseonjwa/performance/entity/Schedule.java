package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule", uniqueConstraints = @UniqueConstraint(
        name = "AK_SCHEDULE_START", columnNames = {"performance_id", "start_at"}))
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

    protected Schedule() {}

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long value) { this.scheduleId = value; }

    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime value) { this.startAt = value; }

    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }

    public Performance getPerformance() { return performance; }
    public void setPerformance(Performance value) { this.performance = value; }
}
