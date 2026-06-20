package com.travelmate.common.money;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeRateCacheTest {

    @Test
    void current_invertsBaseToCurrencyIntoRateToBase() {
        ExchangeRateProvider provider = mock(ExchangeRateProvider.class);
        // 1 VND = 0.00004 USD → 1 USD = 25000 VND (rateToBase).
        when(provider.getRate(eq("VND"), eq("USD"))).thenReturn(new BigDecimal("0.00004"));
        // Every other supported currency returns something so the refresh completes.
        when(provider.getRate(eq("VND"), anyString())).thenReturn(new BigDecimal("0.00004"));

        ExchangeRateCache cache = new ExchangeRateCache(provider);
        ExchangeRateCache.Snapshot snapshot = cache.current();

        assertThat(snapshot.baseCurrency()).isEqualTo("VND");
        assertThat(snapshot.updatedAt()).isNotNull();
        // Base currency is always present with rateToBase == 1.
        BigDecimal vnd = rateOf(snapshot, "VND");
        assertThat(vnd).isEqualByComparingTo(BigDecimal.ONE);
        BigDecimal usd = rateOf(snapshot, "USD");
        assertThat(usd).isEqualByComparingTo(new BigDecimal("25000"));
    }

    @Test
    void refresh_sourceUnreachable_throws503() {
        ExchangeRateProvider provider = mock(ExchangeRateProvider.class);
        when(provider.getRate(eq("VND"), anyString()))
                .thenThrow(new RuntimeException("network down"));

        ExchangeRateCache cache = new ExchangeRateCache(provider);

        ApiException ex = catch_(cache::refresh);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE);
    }

    private static BigDecimal rateOf(ExchangeRateCache.Snapshot snapshot, String currency) {
        return snapshot.rates().stream()
                .filter(r -> r.currency().equals(currency))
                .map(ExchangeRateCache.Rate::rateToBase)
                .findFirst()
                .orElseThrow();
    }

    private static ApiException catch_(Runnable r) {
        try {
            r.run();
        } catch (ApiException ex) {
            return ex;
        }
        throw new AssertionError("Expected ApiException");
    }
}
