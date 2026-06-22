-- V14 — Group tickets. Until now every ticket belonged to exactly one member (MEMBER_ID NOT NULL).
-- Some bookings are shared by the whole group — a single entry pass, one boat charter QR — and
-- belong to nobody in particular. Allow MEMBER_ID to be NULL to represent such a group ticket;
-- everyone on the trip can see it. Existing tickets keep their member.

ALTER TABLE TICKETS MODIFY (MEMBER_ID NULL);
