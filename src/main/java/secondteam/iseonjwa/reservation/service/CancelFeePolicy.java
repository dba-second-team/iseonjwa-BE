package secondteam.iseonjwa.reservation.service;

/**
 * 예매 취소 수수료 정책. (팀 확정 전 예시 값이므로, 바꿀 때는 이 클래스만 수정한다.)
 * <p>
 * 공연 날짜까지 남은 일수(날짜 기준, 한국 시간)로 수수료율을 정한다.
 * <ul>
 *   <li>7일 이상: 0%</li>
 *   <li>3~6일: 10%</li>
 *   <li>1~2일: 30%</li>
 *   <li>당일 또는 이미 지난 공연: 취소 불가</li>
 * </ul>
 * 수수료는 원 단위 미만을 버린다.
 */
public final class CancelFeePolicy {

    private CancelFeePolicy() {
    }

    /** 이 일수 이하(당일, 지난 공연)면 취소할 수 없다. */
    private static final int NOT_ALLOWED_DAYS = 0;

    /**
     * @param daysUntilPerformance 오늘부터 공연 날짜까지 남은 일수 (당일 = 0, 지난 공연은 음수)
     */
    public static boolean isCancelable(long daysUntilPerformance) {
        return daysUntilPerformance > NOT_ALLOWED_DAYS;
    }

    /** 남은 일수에 따른 수수료율(%). 취소 가능한 경우에만 호출한다. */
    public static int feePercent(long daysUntilPerformance) {
        if (daysUntilPerformance >= 7) {
            return 0;
        }
        if (daysUntilPerformance >= 3) {
            return 10;
        }
        return 30;
    }

    /** 결제 금액에 수수료율을 적용한 수수료. 원 단위 미만은 버린다. */
    public static int calculateFee(int paidAmount, long daysUntilPerformance) {
        return (int) ((long) paidAmount * feePercent(daysUntilPerformance) / 100);
    }
}
