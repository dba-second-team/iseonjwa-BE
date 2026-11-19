package secondteam.iseonjwa.seat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
 import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.performance.entity.Schedule;

@Entity
@Table(name = "seat", uniqueConstraints = @UniqueConstraint(name = "AK_SEAT_POSITION",
        columnNames = {"schedule_id", "section", "row_num", "seat_num"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 20)
    private SeatSection section;

    @Column(name = "row_num", nullable = false, length = 10)
    private String rowNum;

    @Column(name = "seat_num", nullable = false)
    private Integer seatNum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "held_until")
    private LocalDateTime heldUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "held_by", foreignKey = @ForeignKey(name = "R_17"))
    private Member heldBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, foreignKey = @ForeignKey(name = "R_18"))
    private Schedule schedule;

    @Builder
    private Seat(SeatSection section, String rowNum, Integer seatNum, Schedule schedule) {
        this.section = section;
        this.rowNum = rowNum;
        this.seatNum = seatNum;
        this.schedule = schedule;
        this.status = SeatStatus.AVAILABLE;
        // held_until, held_by는 선점 시 설정
    }
}
