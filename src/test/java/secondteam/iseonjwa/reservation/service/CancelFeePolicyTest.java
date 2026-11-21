package secondteam.iseonjwa.reservation.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CancelFeePolicyTest {

    @Test
    void 당일이나_지난_공연은_취소할_수_없다() {
        assertThat(CancelFeePolicy.isCancelable(0)).isFalse();
        assertThat(CancelFeePolicy.isCancelable(-1)).isFalse();
        assertThat(CancelFeePolicy.isCancelable(1)).isTrue();
    }

    @Test
    void 공연일까지_남은_일수에_따라_수수료율이_정해진다() {
        assertThat(CancelFeePolicy.feePercent(1)).isEqualTo(30);
        assertThat(CancelFeePolicy.feePercent(2)).isEqualTo(30);
        assertThat(CancelFeePolicy.feePercent(3)).isEqualTo(10);
        assertThat(CancelFeePolicy.feePercent(6)).isEqualTo(10);
        assertThat(CancelFeePolicy.feePercent(7)).isEqualTo(0);
        assertThat(CancelFeePolicy.feePercent(30)).isEqualTo(0);
    }

    @Test
    void 수수료는_원_단위_미만을_버린다() {
        assertThat(CancelFeePolicy.calculateFee(300_000, 3)).isEqualTo(30_000);
        assertThat(CancelFeePolicy.calculateFee(45_000, 1)).isEqualTo(13_500);
        assertThat(CancelFeePolicy.calculateFee(10_005, 3)).isEqualTo(1_000);
        assertThat(CancelFeePolicy.calculateFee(300_000, 7)).isEqualTo(0);
    }
}
