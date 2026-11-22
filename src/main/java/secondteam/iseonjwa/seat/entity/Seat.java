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

    /** 좌석을 선점(HELD) 상태로 바꾸고 선점한 회원과 만료 시각을 기록한다. */
    public void hold(Member member, LocalDateTime until) {
        this.status = SeatStatus.HELD;
        this.heldBy = member;
        this.heldUntil = until;
    }

    /** 해당 회원이 현재 선점(HELD) 중인 좌석인지 확인한다. */
    public boolean isHeldBy(Long memberId) {
        return this.status == SeatStatus.HELD
                && this.heldBy != null
                && this.heldBy.getMemberId().equals(memberId);
    }

    /** 선점 유효 시각이 기준 시각 이후인지(held_until > now) 확인한다. */
    public boolean isHoldValidAt(LocalDateTime now) {
        return this.heldUntil != null && this.heldUntil.isAfter(now);
    }

    /** 좌석을 예매 확정(RESERVED) 상태로 바꾸고 선점 정보를 비운다. */
    public void reserve() {
        this.status = SeatStatus.RESERVED;
        this.heldBy = null;
        this.heldUntil = null;
    }

    /** 좌석을 다시 예매 가능한 상태로 돌리고 선점 정보를 비운다. */
    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.heldBy = null;
        this.heldUntil = null;
    }

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
