package com.travelmate.dashboard.dto;

import com.travelmate.timeline.EventType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Trip dashboard summary (SPEC §7 Module 13). All money is in the trip's base currency. {@code
 * countdownDays} is days from "today" (in the trip timezone) to the start date — negative once the
 * trip has started, null if no start date is set. {@code nextEvent} is null when none is upcoming.
 */
public record DashboardResponse(
        String baseCurrency,
        Long countdownDays,
        BigDecimal totalBudget,
        BigDecimal totalSpent,
        BigDecimal fundBalance,
        NextEvent nextEvent) {

    public record NextEvent(String rid, String title, EventType eventType, Instant startTime) {
    }
}
