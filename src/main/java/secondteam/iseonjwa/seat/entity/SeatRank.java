package secondteam.iseonjwa.seat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "SEAT_RANK")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatRank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_rank_id")
    private Long seatRankId;

    @Column(name = "grade", nullable = false, unique = true, length = 20)
    private String grade;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder
    private SeatRank(String grade, Integer sortOrder) {
        this.grade = grade;
        this.sortOrder = sortOrder;
    }
}
