package com.travelmate.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Polls for due notifications and dispatches them (SPEC §6 Module 14). Single-instance friendly;
 * add ShedLock if more than one instance ever runs so only one polls per cycle. Disabled in tests
 * via {@code app.notifications.scheduler-enabled=false} so they can drive the dispatcher directly.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationJob {

    private final NotificationDispatcher dispatcher;

    public NotificationJob(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-ms:60000}")
    void poll() {
        dispatcher.dispatchDue(Instant.now());
    }
}
