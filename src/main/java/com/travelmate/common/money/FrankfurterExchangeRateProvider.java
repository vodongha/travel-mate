package com.travelmate.common.money;

import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Free, no-key exchange-rate source (SPEC §2.4): <a href="https://frankfurter.app">frankfurter.app</a>.
 * Rates are cached per day per currency pair (the API publishes daily reference rates), so a trip's
 * many expenses on one day hit the network at most once per pair. Callers always store the returned
 * rate as a snapshot and may override it manually.
 */
@Component
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {

    private static final String BASE_URL = "https://api.frankfurter.app";

    private final RestClient restClient;
    private final Map<String, BigDecimal> dailyCache = new ConcurrentHashMap<>();

    public FrankfurterExchangeRateProvider() {
        this.restClient = RestClient.create(BASE_URL);
    }

    @Override
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        String key = LocalDate.now() + ":" + from + ":" + to;
        return dailyCache.computeIfAbsent(key, k -> fetch(from, to));
    }

    private BigDecimal fetch(String from, String to) {
        try {
            FrankfurterResponse body = restClient.get()
                    .uri("/latest?from={from}&to={to}", from, to)
                    .retrieve()
                    .body(FrankfurterResponse.class);
            if (body == null || body.rates() == null || body.rates().get(to) == null) {
                throw rateUnavailable(from, to, null);
            }
            return body.rates().get(to);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw rateUnavailable(from, to, e);
        }
    }

    private static ApiException rateUnavailable(String from, String to, Throwable cause) {
        return new ApiException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                "Could not fetch the " + from + "->" + to + " rate; supply exchangeRate manually.");
    }

    /** Minimal view of the frankfurter response: {@code {"amount":1,"base":"USD","rates":{"VND":...}}}. */
    record FrankfurterResponse(BigDecimal amount, String base, Map<String, BigDecimal> rates) {
    }
}
