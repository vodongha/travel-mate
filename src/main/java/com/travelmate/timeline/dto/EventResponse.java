package com.travelmate.timeline.dto;

import com.travelmate.common.entity.Category;
import com.travelmate.timeline.Event;

import java.time.Instant;

public record EventResponse(
        String rid,
        String title,
        Category eventType,
        Instant startTime,
        Instant endTime,
        String placeRid,
        String note) {

    public static EventResponse from(Event event, String placeRid) {
        return new EventResponse(
                event.getRid(),
                event.getTitle(),
                event.getEventType(),
                event.getStartTime(),
                event.getEndTime(),
                placeRid,
                event.getNote());
    }
}
