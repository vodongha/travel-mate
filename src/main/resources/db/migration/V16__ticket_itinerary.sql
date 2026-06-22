-- V16 — Tickets attach to an itinerary item (polymorphic, like expenses): a flight leg has one
-- boarding-pass ticket per passenger (seat + optional QR), a stay/event its entrance passes. This
-- removes the overlap where transport/accommodation carried their own per-person SEAT/QR_DATA — those
-- now live on the linked ticket, which has an owner (or is a shared/group ticket).
ALTER TABLE TICKETS ADD (ITINERARY_KIND VARCHAR2(20), ITINERARY_ID NUMBER(19));

-- A boarding pass may be seat-only (no scannable QR), so the QR string is now optional.
ALTER TABLE TICKETS MODIFY (QR_DATA NULL);

-- Per-person fields move off the shared itinerary items onto the per-member ticket.
ALTER TABLE TRANSPORTS DROP (SEAT, QR_DATA);
ALTER TABLE ACCOMMODATIONS DROP (QR_DATA);
