package com.travelmate.common.money;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory cache of the app's supported exchange rates against the base currency (VND).
 *
 * <p>Rates are kept here (a {@code @Component}, no DB table — simpler) and refreshed on demand or by
 * a 12h scheduled job (see {@link ExchangeRateScheduler}). Each entry is {@code rateToBase}: how
 * many units of the base currency one unit of that currency is worth (e.g. {@code USD -> ~25000}).
 * It is built by inverting {@link ExchangeRateProvider#getRate(String, String)} which returns the
 * base→currency rate, so a single base→all fetch fills the table.
 *
 * <p>The cache is lazily populated on first read; {@code 503 EXCHANGE_RATE_UNAVAILABLE} surfaces if
 * the source is unreachable and nothing has ever been cached.
 */
@Component
public class ExchangeRateCache {

    private final ExchangeRateProvider provider;

    private final Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> updatedAt = new AtomicReference<>();

    public ExchangeRateCache(ExchangeRateProvider provider) {
        this.provider = provider;
    }

    /** A snapshot of the cache, refreshing first if it has never been populated. */
    public Snapshot current() {
        if (updatedAt.get() == null) {
            refresh();
        }
        return snapshot();
    }

    /**
     * Force a fetch of every supported currency's rate-to-base and replace the cache atomically per
     * entry. Throws {@code 503 EXCHANGE_RATE_UNAVAILABLE} if the source is unreachable; the previous
     * snapshot (if any) is left intact on failure.
     */
    public Snapshot refresh() {
        Map<String, BigDecimal> fresh = new ConcurrentHashMap<>();
        try {
            for (String currency : SupportedCurrencies.ALL) {
                if (currency.equals(SupportedCurrencies.BASE)) {
                    fresh.put(currency, BigDecimal.ONE);
                    continue;
                }
                // provider returns base->currency; invert to get how much base one unit is worth.
                BigDecimal baseToCurrency = provider.getRate(SupportedCurrencies.BASE, currency);
                fresh.put(currency, invert(baseToCurrency));
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                    "Could not fetch exchange rates; the source is unreachable.");
        }
        rates.putAll(fresh);
        updatedAt.set(Instant.now());
        return snapshot();
    }

    private static BigDecimal invert(BigDecimal baseToCurrency) {
        if (baseToCurrency == null || baseToCurrency.signum() == 0) {
            throw new ApiException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE, "Invalid rate from source.");
        }
        // rateToBase = 1 / (base->currency), at the standard rate scale (HALF_UP).
        return BigDecimal.ONE.divide(baseToCurrency, MoneyService.RATE_SCALE, java.math.RoundingMode.HALF_UP);
    }

    private Snapshot snapshot() {
        List<Rate> list = new ArrayList<>();
        for (String currency : SupportedCurrencies.ALL) {
            BigDecimal rate = rates.get(currency);
            if (rate != null) {
                list.add(new Rate(currency, rate));
            }
        }
        return new Snapshot(SupportedCurrencies.BASE, updatedAt.get(), list);
    }

    /** A point-in-time view of the cache. */
    public record Snapshot(String baseCurrency, Instant updatedAt, List<Rate> rates) {
    }

    /** One currency's value expressed in the base currency. */
    public record Rate(String currency, BigDecimal rateToBase) {
    }
}
