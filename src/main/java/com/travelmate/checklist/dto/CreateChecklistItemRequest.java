package com.travelmate.checklist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChecklistItemRequest(
        @NotBlank @Size(max = 300) String title,
        Boolean completed,
        @Size(max = 36) String assigneeRid,
        Integer sortOrder) {
}
