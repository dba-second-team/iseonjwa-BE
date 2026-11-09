package secondteam.iseonjwa.seat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.performance.entity.Venue;

@Entity
@Table(name = "seat", uniqueConstraints = {
    @UniqueConstraint(name = "AK_SEAT_POSITION", columnNames = {"venue_id", "section", "row_num", "seat_num"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "section", nullable = false, length = 20)
    private String section;

    @Column(name = "row_num", nullable = false, length = 10)
    private String rowNum;

    @Column(name = "seat_num", nullable = false)
    private Integer seatNum;

    @Builder
    private Seat(Venue venue, String section, String rowNum, Integer seatNum) {
        this.venue = venue;
        this.section = section;
        this.rowNum = rowNum;
        this.seatNum = seatNum;
    }
}
