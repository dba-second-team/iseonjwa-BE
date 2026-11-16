
package secondteam.iseonjwa.performance.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@NoArgsConstructor
@AllArgsConstructor
public class PriceId implements Serializable {

    private Long performanceId;
    private PriceSeatRank seatRank;

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
