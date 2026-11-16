package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "price")
@IdClass(PriceId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Price {
    @Id
    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_rank", nullable = false, length = 20)
    private PriceSeatRank seatRank;

    @Column(name = "price", nullable = false)
    private Integer price;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performance_id", insertable = false, updatable = false,
            nullable = false, foreignKey = @ForeignKey(name = "R_6"))
    private Performance performance;

    @Builder
    private Price(PriceSeatRank seatRank, Integer price, Performance performance) {
        this.seatRank = seatRank;
        this.price = price;
        this.performance = performance;
        this.performanceId = performance.getPerformanceId();
    }
}
