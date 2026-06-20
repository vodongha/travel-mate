package com.travelmate.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables Spring's {@code @Scheduled} support for the notification dispatch job. */
@Configuration
@EnableScheduling
public class NotificationSchedulingConfig {
}
