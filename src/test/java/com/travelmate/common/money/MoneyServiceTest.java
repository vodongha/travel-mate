package com.travelmate.common.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for the money rounding contract (SPEC §2.4). No Spring context / Docker. */
class MoneyServiceTest {

    private final MoneyService money = new MoneyService();

    @Test
    void toAmountBase_roundsHalfUpToScale4() {
        // 100.00 * 0.123455 = 12.3455 -> HALF_UP at scale 4 stays 12.3455
        BigDecimal base = money.toAmountBase(new BigDecimal("100.00"), new BigDecimal("0.123455"));
        assertThat(base).isEqualByComparingTo("12.3455");
        assertThat(base.scale()).isEqualTo(4);
    }

    @Test
    void toAmountBase_roundsHalfUpOnTie() {
        // 1 * 0.00005 = 0.00005 -> rounds up to 0.0001 at scale 4 (HALF_UP)
        BigDecimal base = money.toAmountBase(BigDecimal.ONE, new BigDecimal("0.00005"));
        assertThat(base).isEqualByComparingTo("0.0001");
    }

    @Test
    void toAmountBase_identityRateWhenSameCurrency() {
        BigDecimal base = money.toAmountBase(new BigDecimal("250000.0000"), BigDecimal.ONE);
        assertThat(base).isEqualByComparingTo("250000.0000");
    }

    @Test
    void normalizeAmount_pinsScaleTo4() {
        assertThat(money.normalizeAmount(new BigDecimal("9.9"))).isEqualByComparingTo("9.9000");
        assertThat(money.normalizeAmount(new BigDecimal("9.99999"))).isEqualByComparingTo("10.0000");
    }

    @Test
    void normalizeRate_pinsScaleTo8() {
        assertThat(money.normalizeRate(new BigDecimal("0.5"))).isEqualByComparingTo("0.50000000");
    }
}
