package com.travelmate.common.money;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single place money rounding and base-currency conversion live (SPEC §2.4, CLAUDE.md "Money
 * rules"). Pinning scale and rounding here — and testing it — is what keeps totals from drifting
 * between environments.
 *
 * <ul>
 *   <li>Amounts use {@link #AMOUNT_SCALE} (4) with {@link RoundingMode#HALF_UP}.</li>
 *   <li>{@code AMOUNT_BASE = AMOUNT * EXCHANGE_RATE}, rounded once, never recomputed later.</li>
 *   <li>If the transaction currency equals the trip base currency, the rate is exactly {@code 1}.</li>
 * </ul>
 *
 * Settlement math (M6) runs on integer minor units, not divided {@link BigDecimal}; that lives
 * with the settlement engine. This service owns the {@link BigDecimal} boundary.
 */
@Service
public class MoneyService {

    /** Money columns are {@code NUMBER(19,4)}. */
    public static final int AMOUNT_SCALE = 4;

    /** Exchange-rate columns are {@code NUMBER(19,8)}. */
    public static final int RATE_SCALE = 8;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Normalize a monetary amount to the fixed money scale. */
    public BigDecimal normalizeAmount(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, ROUNDING);
    }

    /** Normalize an exchange rate to the fixed rate scale. */
    public BigDecimal normalizeRate(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, ROUNDING);
    }

    /**
     * Compute {@code AMOUNT_BASE = amount * rate}, rounded once with {@link RoundingMode#HALF_UP}
     * to {@link #AMOUNT_SCALE}. This is the canonical conversion; callers store the result as a
     * snapshot and never recompute it.
     */
    public BigDecimal toAmountBase(BigDecimal amount, BigDecimal exchangeRate) {
        return amount.multiply(exchangeRate).setScale(AMOUNT_SCALE, ROUNDING);
    }
}
