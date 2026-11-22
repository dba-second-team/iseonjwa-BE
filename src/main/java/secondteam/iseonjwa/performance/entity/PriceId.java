
package secondteam.iseonjwa.performance.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.seat.entity.SeatSection;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PriceId implements Serializable {

    private Long performanceId;
    private SeatSection section;
}
