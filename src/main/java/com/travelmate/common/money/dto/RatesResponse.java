package com.travelmate.common.money.dto;

import com.travelmate.common.money.ExchangeRateCache;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Exchange-rate table the app's currency converter consumes: every supported currency's value in
 * the base currency (VND), plus when it was last refreshed.
 */
public record RatesResponse(String baseCurrency, Instant updatedAt, List<RateEntry> rates) {

    public record RateEntry(String currency, BigDecimal rateToBase) {
    }

    public static RatesResponse from(ExchangeRateCache.Snapshot snapshot) {
        List<RateEntry> entries = snapshot.rates().stream()
                .map(r -> new RateEntry(r.currency(), r.rateToBase()))
                .toList();
        return new RatesResponse(snapshot.baseCurrency(), snapshot.updatedAt(), entries);
    }
}
