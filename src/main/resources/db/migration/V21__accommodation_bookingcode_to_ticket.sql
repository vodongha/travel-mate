-- Move the accommodation booking code onto the ticket, mirroring V20 for transport. The stay holds
-- only name/where/when; the booking voucher code lives on the (group) ticket linked to the stay, and
-- the stay's read-only detail reads it back from the caller's ticket. Backfill ACCOMMODATION tickets
-- from their linked stay, then drop the now-unused column. TICKETS.BOOKING_CODE already exists (V20).

UPDATE TICKETS t
SET BOOKING_CODE = (
        SELECT a.BOOKING_CODE
        FROM ACCOMMODATIONS a
        WHERE a.ID = t.ITINERARY_ID)
WHERE t.ITINERARY_KIND = 'ACCOMMODATION'
  AND t.BOOKING_CODE IS NULL
  AND EXISTS (SELECT 1 FROM ACCOMMODATIONS a WHERE a.ID = t.ITINERARY_ID);

ALTER TABLE ACCOMMODATIONS DROP COLUMN BOOKING_CODE;
