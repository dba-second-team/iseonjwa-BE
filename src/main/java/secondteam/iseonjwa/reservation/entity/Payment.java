package secondteam.iseonjwa.reservation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "paid_amount", nullable = false)
    private Integer paidAmount;

    @Column(name = "cancel_fee", nullable = false)
    private Integer cancelFee = 0;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Builder
    private Payment(Reservation reservation, PaymentMethod paymentMethod, Integer paidAmount,
                    Integer cancelFee, Integer refundAmount, PaymentStatus status,
                    LocalDateTime paidAt, LocalDateTime refundedAt) {
        this.reservation = reservation;
        this.paymentMethod = paymentMethod;
        this.paidAmount = paidAmount;
        this.cancelFee = cancelFee == null ? 0 : cancelFee;
        this.refundAmount = refundAmount == null ? 0 : refundAmount;
        this.status = status;
        this.paidAt = paidAt;
        this.refundedAt = refundedAt;
    }
}
