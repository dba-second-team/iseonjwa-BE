
package secondteam.iseonjwa.performance.entity;

import java.io.Serializable;
import java.util.Objects;

public class PriceId implements Serializable {

    private Long performanceId;
    private PriceSeatRank seatRank;

    public PriceId() {
    }

    public PriceId(Long performanceId, PriceSeatRank seatRank) {
        this.performanceId = performanceId;
        this.seatRank = seatRank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PriceId priceId)) return false;

        return Objects.equals(performanceId, priceId.performanceId)
                && seatRank == priceId.seatRank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(performanceId, seatRank);
    }
}
