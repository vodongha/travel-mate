package com.travelmate.checklist.dto;

import com.travelmate.checklist.ChecklistItem;

public record ChecklistItemResponse(
        String rid,
        String title,
        boolean completed,
        String assigneeRid,
        Integer sortOrder) {

    public static ChecklistItemResponse from(ChecklistItem item, String assigneeRid) {
        return new ChecklistItemResponse(
                item.getRid(),
                item.getTitle(),
                item.isCompleted(),
                assigneeRid,
                item.getSortOrder());
    }
}
