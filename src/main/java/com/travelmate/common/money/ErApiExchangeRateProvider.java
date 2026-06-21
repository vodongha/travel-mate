package com.travelmate.common.money;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.travelmate.common.exception.ApiException;
import com.travelmate.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Free, no-key exchange-rate source (SPEC §2.4): <a href="https://open.er-api.com">open.er-api.com</a>
 * (exchangerate-api.com's open endpoint). Unlike ECB-based sources (frankfurter), it <b>supports
 * VND</b> — the app's base currency — which is why we use it: a frankfurter {@code from=VND} fetch
 * returns nothing, so the whole rates feature 503'd.
 *
 * <p>{@code GET /v6/latest/{from}} returns {@code {"result":"success","rates":{"USD":…,"VND":1,…}}}
 * where each value is how many of that currency one unit of {@code from} buys. We cache the rate map
 * per (day, from) so a trip's many same-day expenses hit the network at most once per source
 * currency. Callers snapshot the returned rate; the user may always override it manually.
 */
@Component
public class ErApiExchangeRateProvider implements ExchangeRateProvider {

    private static final String BASE_URL = "https://open.er-api.com/v6";

    private final RestClient restClient;
    private final Map<String, Map<String, BigDecimal>> dailyCache = new ConcurrentHashMap<>();

    public ErApiExchangeRateProvider() {
        this.restClient = RestClient.create(BASE_URL);
    }

    @Override
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }
        Map<String, BigDecimal> rates = dailyCache.computeIfAbsent(LocalDate.now() + ":" + from,
                k -> fetch(from));
        BigDecimal rate = rates.get(to);
        if (rate == null) {
            throw unavailable(from + "->" + to);
        }
        return rate;
    }

    private Map<String, BigDecimal> fetch(String from) {
        try {
            ErApiResponse body = restClient.get()
                    .uri("/latest/{from}", from)
                    .retrieve()
                    .body(ErApiResponse.class);
            if (body == null || !"success".equals(body.result()) || body.rates() == null
                    || body.rates().isEmpty()) {
                throw unavailable(from);
            }
            return body.rates();
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable(from);
        }
    }

    private static ApiException unavailable(String what) {
        return new ApiException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                "Could not fetch the " + what + " rate; supply exchangeRate manually.");
    }

    /** Minimal view of the open.er-api response (unknown fields like base_code/provider ignored). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErApiResponse(String result, Map<String, BigDecimal> rates) {
    }
}
