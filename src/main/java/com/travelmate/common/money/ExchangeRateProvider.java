package com.travelmate.common.money;

import java.math.BigDecimal;

/**
 * Looks up a market exchange rate from {@code from} to {@code to} (SPEC §2.4). The result is only
 * a default — every money transaction <b>snapshots</b> the rate it used, and the client may always
 * override it manually (real money-changer rates differ from the market). Implementations should
 * cache per day. A same-currency lookup returns exactly {@code 1}.
 */
public interface ExchangeRateProvider {

    BigDecimal getRate(String fromCurrency, String toCurrency);
}
