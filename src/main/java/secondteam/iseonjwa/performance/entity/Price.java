package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "price")
@IdClass(PriceId.class)
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

    protected Price() {}

    public Long getPerformanceId() { return performanceId; }
    public void setPerformanceId(Long value) { this.performanceId = value; }

    public PriceSeatRank getSeatRank() { return seatRank; }
    public void setSeatRank(PriceSeatRank value) { this.seatRank = value; }

    public Integer getPrice() { return price; }
    public void setPrice(Integer value) { this.price = value; }

    public Performance getPerformance() { return performance; }
    public void setPerformance(Performance value) { this.performance = value; }
}
