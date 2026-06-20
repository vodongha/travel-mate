-- V8 — Settings support: add an optional phone number to USERS (SPEC §7 Module 1).
-- Oracle conventions (CLAUDE.md "Oracle gotchas"):
--   * optional text is nullable and written as NULL, never '' (Oracle stores '' as NULL).
--   * VARCHAR2 sized for the longest real value: E.164 numbers are at most 15 digits, plus a
--     leading '+' and room for formatting → 32 is comfortable.
ALTER TABLE USERS ADD (PHONE VARCHAR2(32));
