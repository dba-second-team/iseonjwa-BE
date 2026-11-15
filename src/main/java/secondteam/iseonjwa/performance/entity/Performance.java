package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "performance")
public class Performance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long performanceId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "genre", nullable = false, length = 20)
    private Genre genre;

    @Column(name = "running_time", nullable = false)
    private Integer runningTime;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PerformanceStatus status;

    @Column(name = "venue_name", nullable = false, length = 200)
    private String venueName;

    @Column(name = "max_tickets_per_member", nullable = false)
    private Integer maxTicketsPerMember;

    protected Performance() {}

    public Long getPerformanceId() { return performanceId; }
    public void setPerformanceId(Long value) { this.performanceId = value; }

    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }

    public Genre getGenre() { return genre; }
    public void setGenre(Genre value) { this.genre = value; }

    public Integer getRunningTime() { return runningTime; }
    public void setRunningTime(Integer value) { this.runningTime = value; }

    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }

    public PerformanceStatus getStatus() { return status; }
    public void setStatus(PerformanceStatus value) { this.status = value; }

    public String getVenueName() { return venueName; }
    public void setVenueName(String value) { this.venueName = value; }

    public Integer getMaxTicketsPerMember() { return maxTicketsPerMember; }
    public void setMaxTicketsPerMember(Integer value) { this.maxTicketsPerMember = value; }
}
