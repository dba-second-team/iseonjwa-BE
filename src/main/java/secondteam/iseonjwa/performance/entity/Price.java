package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.seat.entity.SeatRank;

@Entity
@Table(name = "PRICE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Price {
    @EmbeddedId
    private PriceId id;

    @MapsId("performanceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @MapsId("seatRankId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_rank_id", nullable = false)
    private SeatRank seatRank;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Builder
    private Price(Performance performance, SeatRank seatRank, Integer price) {
        this.performance = performance;
        this.seatRank = seatRank;
        this.price = price;
        this.id = new PriceId(performance.getPerformanceId(), seatRank.getSeatRankId());
        // Both references must already be persisted before constructing a Price.
    }
}
