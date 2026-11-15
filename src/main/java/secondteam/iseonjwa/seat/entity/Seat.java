package secondteam.iseonjwa.seat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.performance.entity.Schedule;

@Entity
@Table(name = "seat", uniqueConstraints = @UniqueConstraint(name = "AK_SEAT_POSITION",
        columnNames = {"schedule_id", "section", "row_num", "seat_num"}))
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
    @Column(name = "seat_rank", nullable = false, length = 20)
    private SeatRank seatRank;

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

    protected Seat() {}

    public Long getSeatId() { return seatId; }
    public void setSeatId(Long value) { this.seatId = value; }

    public SeatSection getSection() { return section; }
    public void setSection(SeatSection value) { this.section = value; }

    public String getRowNum() { return rowNum; }
    public void setRowNum(String value) { this.rowNum = value; }

    public Integer getSeatNum() { return seatNum; }
    public void setSeatNum(Integer value) { this.seatNum = value; }

    public SeatRank getSeatRank() { return seatRank; }
    public void setSeatRank(SeatRank value) { this.seatRank = value; }

    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus value) { this.status = value; }

    public LocalDateTime getHeldUntil() { return heldUntil; }
    public void setHeldUntil(LocalDateTime value) { this.heldUntil = value; }

    public Member getHeldBy() { return heldBy; }
    public void setHeldBy(Member value) { this.heldBy = value; }

    public Schedule getSchedule() { return schedule; }
    public void setSchedule(Schedule value) { this.schedule = value; }
}
