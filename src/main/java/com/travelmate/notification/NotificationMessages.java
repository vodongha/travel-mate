package com.travelmate.notification;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Renders notification title/body from a message key + args, in a recipient device's language.
 * Bundles live in {@code resources/i18n/notifications_{en,vi}.properties}; an unknown or missing
 * language falls back to English (never the server's system locale).
 */
@Component
public class NotificationMessages {

    private final MessageSource source;

    public NotificationMessages(MessageSource notificationMessageSource) {
        this.source = notificationMessageSource;
    }

    /** Localized message for [key] with positional [args], in [locale] (English fallback). */
    public String render(String key, List<String> args, Locale locale) {
        return source.getMessage(key, args == null ? null : args.toArray(), locale);
    }

    /** A BCP-47 tag ("vi", "en", null/blank) → a Locale; anything unrecognized maps to English. */
    public Locale localeOf(String tag) {
        if (tag == null || tag.isBlank()) {
            return Locale.ENGLISH;
        }
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }

    /** The notification bundle, isolated from any app-wide {@code messageSource}. */
    @Bean
    MessageSource notificationMessageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/notifications");
        ms.setDefaultEncoding("UTF-8");
        ms.setDefaultLocale(Locale.ENGLISH);
        ms.setFallbackToSystemLocale(false);
        return ms;
    }
}
