package secondteam.iseonjwa.reservation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment", uniqueConstraints = @UniqueConstraint(
        name = "AK_PAYMENT_RESERVATION", columnNames = "reservation_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "paid_amount", nullable = false)
    private Integer paidAmount;

    @Column(name = "cancel_fee", nullable = false)
    private Integer cancelFee;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "R_16"))
    private Reservation reservation;

    @Builder
    private Payment(Integer paidAmount, Reservation reservation) {
        this.paidAmount = paidAmount;
        this.cancelFee = 0;
        this.refundAmount = 0;
        this.status = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
        this.reservation = reservation;
        // refunded_at은 필요 시 설정
    }
}
