package com.travelmate.timeline.dto;

import com.travelmate.timeline.Event;
import com.travelmate.timeline.EventType;

import java.time.Instant;

public record EventResponse(
        String rid,
        String title,
        EventType eventType,
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
