package secondteam.iseonjwa.reservation.dto;

/**
 * 가상 결제 수단.
 * <p>
 * 요청 입력값 검증용으로만 사용하며 DB(payment 테이블)에는 저장하지 않는다.
 */
public enum PaymentMethod {
    CARD,
    BANK_TRANSFER
}
