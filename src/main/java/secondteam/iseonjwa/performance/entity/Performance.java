package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "performance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
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

    @Builder
    private Performance(String title, Genre genre, Integer runningTime, String description, PerformanceStatus status,
                       String venueName, Integer maxTicketsPerMember) {
        this.title = title;
        this.genre = genre;
        this.runningTime = runningTime;
        this.description = description;
        this.status = status;
        this.venueName = venueName;
        this.maxTicketsPerMember = maxTicketsPerMember;
    }
}
