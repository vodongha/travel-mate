package com.travelmate.common.audit;

import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Supplies the current user's internal {@code ID} for {@code CREATED_BY} / {@code UPDATED_BY}.
 *
 * <p>M1 has no authentication yet, so this returns empty (writes are attributed to "system",
 * i.e. {@code null}). M2 wires this to the {@code SecurityContext} to return the authenticated
 * user's id.
 */
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        // TODO(M2): resolve from SecurityContext once authentication lands.
        return Optional.empty();
    }
}
