package com.travelmate.common.audit;

import com.travelmate.common.security.AuthPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Supplies the current user's internal {@code ID} for {@code CREATED_BY} / {@code UPDATED_BY}.
 * Reads it from the {@link AuthPrincipal} in the {@code SecurityContext}; unauthenticated writes
 * (system / pre-login flows) are attributed to {@code null}.
 */
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return Optional.ofNullable(principal.id());
        }
        return Optional.empty();
    }
}
