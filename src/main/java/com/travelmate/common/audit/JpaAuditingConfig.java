package com.travelmate.common.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so {@code @CreatedDate}/{@code @LastModifiedDate}/{@code @CreatedBy}/
 * {@code @LastModifiedBy} on {@link com.travelmate.common.entity.BaseEntity} are populated
 * automatically.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    AuditorAware<Long> auditorAware() {
        return new AuditorAwareImpl();
    }
}
