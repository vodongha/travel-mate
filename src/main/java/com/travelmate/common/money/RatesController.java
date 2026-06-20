package com.travelmate.common.money;

import com.travelmate.common.money.dto.RatesResponse;
import com.travelmate.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exchange rates for the app's currency converter (SPEC §2.4). Authenticated (the default
 * {@code anyRequest().authenticated()} covers it). Rates are served from an in-memory cache that a
 * 12h scheduled job keeps fresh; {@code 503 EXCHANGE_RATE_UNAVAILABLE} surfaces if the source is
 * unreachable and nothing is cached yet.
 */
@RestController
@RequestMapping("/api/v1/rates")
public class RatesController {

    private final ExchangeRateCache cache;

    public RatesController(ExchangeRateCache cache) {
        this.cache = cache;
    }

    @GetMapping
    public ApiResponse<RatesResponse> rates() {
        return ApiResponse.ok(RatesResponse.from(cache.current()));
    }

    /** Force a fetch (the same fetch the 12h job runs); 503 if the source is unreachable. */
    @PostMapping("/refresh")
    public ApiResponse<RatesResponse> refresh() {
        return ApiResponse.ok(RatesResponse.from(cache.refresh()));
    }
}
