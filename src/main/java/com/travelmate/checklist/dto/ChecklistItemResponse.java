package com.travelmate.checklist.dto;

import com.travelmate.checklist.ChecklistItem;

public record ChecklistItemResponse(
        String rid,
        String title,
        // The CALLER's own completion of this item (each member ticks their own copy).
        boolean completed,
        String assigneeRid,
        Integer sortOrder) {

    public static ChecklistItemResponse from(ChecklistItem item, String assigneeRid, boolean completed) {
        return new ChecklistItemResponse(
                item.getRid(),
                item.getTitle(),
                completed,
                assigneeRid,
                item.getSortOrder());
    }
}
