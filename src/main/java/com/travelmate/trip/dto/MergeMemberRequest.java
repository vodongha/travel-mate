package com.travelmate.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Merge the path member (the source, typically a ghost) into {@code targetRid} (the surviving
 * member): every money/ticket/checklist reference moves from the source to the target and the source
 * is then removed. Both must belong to the trip and be different members.
 */
public record MergeMemberRequest(
        @NotBlank @Size(max = 36) String targetRid) {
}
