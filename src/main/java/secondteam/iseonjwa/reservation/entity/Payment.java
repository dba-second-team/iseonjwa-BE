package secondteam.iseonjwa.reservation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment", uniqueConstraints = @UniqueConstraint(
        name = "AK_PAYMENT_RESERVATION", columnNames = "reservation_id"))
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "paid_amount", nullable = false)
    private Integer paidAmount;

    @Column(name = "cancel_fee", nullable = false)
    private Integer cancelFee = 0;

    @Column(name = "refund_amount", nullable = false)
    private Integer refundAmount = 0;

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

    protected Payment() {}

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long value) { this.paymentId = value; }

    public Integer getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Integer value) { this.paidAmount = value; }

    public Integer getCancelFee() { return cancelFee; }
    public void setCancelFee(Integer value) { this.cancelFee = value; }

    public Integer getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Integer value) { this.refundAmount = value; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus value) { this.status = value; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime value) { this.paidAt = value; }

    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime value) { this.refundedAt = value; }

    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation value) { this.reservation = value; }
}
