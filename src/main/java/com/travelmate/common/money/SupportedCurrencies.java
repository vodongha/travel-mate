package com.travelmate.common.money;

import java.util.List;
import java.util.Set;

/**
 * The small set of ISO-4217 currencies the app surfaces in the exchange-rate endpoint and validates
 * profile / trip currencies against (SPEC §2.4). {@link #BASE} is the reporting base (VND). This is
 * a UI/convenience set, not a hard limit on what a money transaction may snapshot — the rate
 * provider can still fetch any pair the source supports.
 */
public final class SupportedCurrencies {

    /** Reporting base currency. */
    public static final String BASE = "VND";

    /** Ordered for stable API output (base first, then the rest the app supports). */
    public static final List<String> ALL = List.of(
            "VND", "USD", "EUR", "JPY", "KRW", "THB", "SGD", "CNY", "AUD", "GBP");

    private static final Set<String> SET = Set.copyOf(ALL);

    private SupportedCurrencies() {
    }

    public static boolean isSupported(String code) {
        return code != null && SET.contains(code.toUpperCase());
    }
}
