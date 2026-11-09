package secondteam.iseonjwa.seat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.performance.entity.Performance;

@Entity
@Table(name = "PERFORMANCE_SEAT", uniqueConstraints = {
    @UniqueConstraint(name = "AK_PERFORMANCE_SEAT", columnNames = {"performance_id", "seat_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_seat_id")
    private Long performanceSeatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_rank_id", nullable = false)
    private SeatRank seatRank;

    @Builder
    private PerformanceSeat(Performance performance, Seat seat, SeatRank seatRank) {
        this.performance = performance;
        this.seat = seat;
        this.seatRank = seatRank;
    }
}
