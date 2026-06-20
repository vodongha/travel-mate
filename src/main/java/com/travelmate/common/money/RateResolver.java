package com.travelmate.common.money;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Resolves the snapshot exchange rate for a money transaction (SPEC §2.4), shared by expenses and
 * fund entries: exactly {@code 1} when the currency equals the trip base; a manual override if the
 * client supplied one (real money-changer rates differ from market); otherwise the market rate from
 * {@link ExchangeRateProvider}. The result is normalized to the rate scale and stored as a snapshot.
 */
@Component
public class RateResolver {

    private final MoneyService moneyService;
    private final ExchangeRateProvider exchangeRateProvider;

    public RateResolver(MoneyService moneyService, ExchangeRateProvider exchangeRateProvider) {
        this.moneyService = moneyService;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    public BigDecimal resolve(String currency, String baseCurrency, BigDecimal override) {
        if (currency.equalsIgnoreCase(baseCurrency)) {
            return BigDecimal.ONE.setScale(MoneyService.RATE_SCALE);
        }
        if (override != null) {
            return moneyService.normalizeRate(override);
        }
        return moneyService.normalizeRate(exchangeRateProvider.getRate(currency, baseCurrency));
    }
}
