-- V15 — Unify the per-feature classification enums (EventType, PlaceType, TicketType) onto the single
-- canonical Category. The columns already store the enum name as VARCHAR2(20); here we remap the
-- legacy values that only existed in the per-feature enums to their Category equivalent. This is
-- intentionally lossy where a per-feature enum was finer-grained than Category (POI granularity):
--   places: HOTEL→ACCOMMODATION, RESTAURANT→FOOD, ATTRACTION→SIGHTSEEING, AIRPORT/STATION→TRANSPORT
--   events: HOTEL→ACCOMMODATION
--   tickets: EVENT→ACTIVITY
-- All surviving values are valid Category names. Columns and indexes are unchanged.

UPDATE EVENTS  SET EVENT_TYPE  = 'ACCOMMODATION' WHERE EVENT_TYPE  = 'HOTEL';

UPDATE PLACES  SET PLACE_TYPE  = 'ACCOMMODATION' WHERE PLACE_TYPE  = 'HOTEL';
UPDATE PLACES  SET PLACE_TYPE  = 'FOOD'          WHERE PLACE_TYPE  = 'RESTAURANT';
UPDATE PLACES  SET PLACE_TYPE  = 'SIGHTSEEING'   WHERE PLACE_TYPE  = 'ATTRACTION';
UPDATE PLACES  SET PLACE_TYPE  = 'TRANSPORT'     WHERE PLACE_TYPE  IN ('AIRPORT', 'STATION');

UPDATE TICKETS SET TICKET_TYPE = 'ACTIVITY'      WHERE TICKET_TYPE = 'EVENT';
