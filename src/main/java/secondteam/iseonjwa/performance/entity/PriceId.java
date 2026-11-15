package secondteam.iseonjwa.performance.entity;

import java.io.Serializable;
import java.util.Objects;

public class PriceId implements Serializable {
    private Long performanceId;
    private String seatRank;

    public PriceId() {}

    public PriceId(Long performanceId, String seatRank) {
        this.performanceId = performanceId;
        this.seatRank = seatRank;
    }

    public Long getPerformanceId() { return performanceId; }
    public String getSeatRank() { return seatRank; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PriceId that)) return false;
        return Objects.equals(performanceId, that.performanceId)
                && Objects.equals(seatRank, that.seatRank);
    }

    @Override
    public int hashCode() { return Objects.hash(performanceId, seatRank); }
}
