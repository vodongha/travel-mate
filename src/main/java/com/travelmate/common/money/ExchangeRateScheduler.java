package com.travelmate.common.money;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the {@link ExchangeRateCache} every 12 hours on a fixed wall-clock schedule (SPEC §2.4).
 *
 * <p>A {@code cron} (00:00 and 12:00 ICT) is used rather than {@code fixedRate}/{@code fixedDelay}
 * on purpose: a relative interval restarts its countdown on every app restart/deploy, so frequent
 * restarts would keep postponing the run. The clock cron fires at the same times regardless. The
 * notification dispatcher uses the same {@code @ConditionalOnProperty} toggle so integration tests
 * can keep the background job off and drive the cache directly.
 */
@Component
@ConditionalOnProperty(name = "app.rates.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ExchangeRateScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateScheduler.class);

    private final ExchangeRateCache cache;

    public ExchangeRateScheduler(ExchangeRateCache cache) {
        this.cache = cache;
    }

    /** Fire at 00:00 and 12:00 Indochina Time (every 12h on the clock, not relative to start-up). */
    @Scheduled(cron = "${app.rates.refresh-cron:0 0 0,12 * * *}", zone = "Asia/Ho_Chi_Minh")
    void refresh() {
        try {
            cache.refresh();
            log.info("Exchange rates refreshed.");
        } catch (RuntimeException e) {
            // Don't let a transient source outage kill the schedule; the cache keeps its last values.
            log.warn("Scheduled exchange-rate refresh failed: {}", e.getMessage());
        }
    }
}
