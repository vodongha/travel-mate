-- V13 — Generalise the expense→timeline link. V10 added EVENT_ID (an expense could attach only to
-- an EVENTS row), but the timeline is really three independent tables — EVENTS, TRANSPORT and
-- ACCOMMODATION. Replace the single FK with a polymorphic pair (ITINERARY_KIND, ITINERARY_ID) so an
-- expense can attach to any itinerary item. No real FK is possible across three tables, so the
-- target is validated in the service layer instead. Existing EVENT_ID links are migrated to
-- kind='EVENT'. CASCADE CONSTRAINTS drops the old FK (and its column index) with the column.

ALTER TABLE EXPENSES ADD (ITINERARY_KIND VARCHAR2(20), ITINERARY_ID NUMBER(19));

UPDATE EXPENSES SET ITINERARY_KIND = 'EVENT', ITINERARY_ID = EVENT_ID WHERE EVENT_ID IS NOT NULL;

ALTER TABLE EXPENSES DROP COLUMN EVENT_ID CASCADE CONSTRAINTS;

CREATE INDEX IX_EXPENSES_ITINERARY ON EXPENSES (ITINERARY_KIND, ITINERARY_ID);
