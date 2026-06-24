package com.travelmate.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** The notification message bundle renders title/body per locale, falling back to English. */
class NotificationMessagesTest {

    private NotificationMessages messages;

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/notifications");
        ms.setDefaultEncoding("UTF-8");
        ms.setDefaultLocale(Locale.ENGLISH);
        ms.setFallbackToSystemLocale(false);
        messages = new NotificationMessages(ms);
    }

    @Test
    void rendersEnglish() {
        assertThat(messages.render("notif.debt.title", List.of("Bali"), messages.localeOf("en")))
                .isEqualTo("Settle up — Bali");
        assertThat(messages.render("notif.event.body", List.of("30"), messages.localeOf("en")))
                .isEqualTo("Starts in 30 minutes.");
    }

    @Test
    void rendersVietnamese() {
        assertThat(messages.render("notif.debt.title", List.of("Bali"), messages.localeOf("vi")))
                .isEqualTo("Quyết toán — Bali");
        assertThat(messages.render("notif.event.body", List.of("30"), messages.localeOf("vi")))
                .isEqualTo("Bắt đầu sau 30 phút.");
    }

    @Test
    void unknownOrNullLocaleFallsBackToEnglish() {
        assertThat(messages.localeOf(null)).isEqualTo(Locale.ENGLISH);
        assertThat(messages.localeOf("")).isEqualTo(Locale.ENGLISH);
        assertThat(messages.render("notif.checkin.body", List.of(), messages.localeOf("zz")))
                .isEqualTo("It's check-in time.");
    }
}
