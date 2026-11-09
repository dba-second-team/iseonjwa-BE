package secondteam.iseonjwa.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "seat_rank_id", nullable = false)
    private Long seatRankId;

    public PriceId(Long performanceId, Long seatRankId) {
        this.performanceId = performanceId;
        this.seatRankId = seatRankId;
    }
}
