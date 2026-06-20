package com.travelmate.checklist.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update — only non-null fields are applied. {@code assigneeRid}: a blank string clears the
 * assignee, omitting it (null) leaves it unchanged.
 */
public record UpdateChecklistItemRequest(
        @Size(max = 300) String title,
        Boolean completed,
        @Size(max = 36) String assigneeRid,
        Integer sortOrder) {
}
