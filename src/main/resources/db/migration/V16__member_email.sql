-- V16 — optional email on a (ghost) member: keeps the friendly display name but lets the
-- invitation accept auto-merge the ghost into the real account when they join with this email
-- (see InvitationService.findGhostToMerge). Also used by the manual "merge member" action.
ALTER TABLE TRIP_MEMBERS ADD (EMAIL VARCHAR2(320));
