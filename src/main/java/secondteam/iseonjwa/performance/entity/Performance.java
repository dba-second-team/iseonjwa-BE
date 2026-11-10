package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "performance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long performanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "genre", nullable = false, length = 20)
    private Genre genre;


    /** Running time in seconds. */
    @Column(name = "running_time", nullable = false)
    private Integer runningTime;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PerformanceStatus status;

    @Builder
    private Performance(Venue venue, String title, Genre genre, Integer runningTime,
                        String description, PerformanceStatus status) {
        this.venue = venue;
        this.title = title;
        this.genre = genre;
        this.runningTime = runningTime;
        this.description = description;
        this.status = status;
    }
}
