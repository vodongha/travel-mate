-- Move carrier (PROVIDER) + booking code (BOOKING_CODE) from the transport leg onto the ticket.
-- Rationale: the itinerary leg holds only what/where/when; each person's ticket carries their own
-- carrier + booking/PNR code (and seat + QR). The "My tickets" screen and the leg's read-only detail
-- both read these from the caller's linked ticket. Backfill existing tickets from the leg they link
-- to, then drop the now-unused leg columns.

ALTER TABLE TICKETS ADD (PROVIDER VARCHAR2(150), BOOKING_CODE VARCHAR2(100));

UPDATE TICKETS t
SET (PROVIDER, BOOKING_CODE) = (
        SELECT tr.PROVIDER, tr.BOOKING_CODE
        FROM TRANSPORTS tr
        WHERE tr.ID = t.ITINERARY_ID)
WHERE t.ITINERARY_KIND = 'TRANSPORT'
  AND EXISTS (SELECT 1 FROM TRANSPORTS tr WHERE tr.ID = t.ITINERARY_ID);

ALTER TABLE TRANSPORTS DROP (PROVIDER, BOOKING_CODE);
