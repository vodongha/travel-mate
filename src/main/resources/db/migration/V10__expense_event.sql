-- V10 — Link an expense to a timeline event (optional). Supports the itinerary-centric flow:
-- an expense that "came up" during an event ("ăn trưa ở X", "vé tham quan") can be attached to
-- that EVENTS row, so the timeline can show its costs. Nullable; existing expenses keep EVENT_ID null.

ALTER TABLE EXPENSES ADD (EVENT_ID NUMBER(19));
ALTER TABLE EXPENSES ADD CONSTRAINT FK_EXPENSES_EVENT FOREIGN KEY (EVENT_ID) REFERENCES EVENTS (ID);
CREATE INDEX IX_EXPENSES_EVENT ON EXPENSES (EVENT_ID);
