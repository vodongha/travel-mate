-- Create a dedicated schema for TravelMate so several apps can share one Oracle
-- Autonomous Database without colliding. Run this as ADMIN (e.g. in the OCI
-- console -> Database Actions -> SQL). It contains no secrets.
--
-- Design (mirrors the sibling family-budget project): a SCHEMA-ONLY account
-- (NO AUTHENTICATION) — it owns the tables but cannot log in, so there is no
-- extra password to manage. The app keeps connecting as ADMIN and sets
-- ORACLE_SCHEMA=TRAVEL_MATE, which makes every connection run
-- `ALTER SESSION SET CURRENT_SCHEMA = TRAVEL_MATE`. Flyway then creates its
-- schema-history table and all business tables in this schema, and JPA reads
-- and writes there.

-- 1) Schema-only owner + storage quota.
CREATE USER TRAVEL_MATE NO AUTHENTICATION;
ALTER USER TRAVEL_MATE QUOTA UNLIMITED ON DATA;

-- (ADMIN already has privilege to SET CURRENT_SCHEMA to any schema, so no extra
--  grant is needed for the current-schema approach. Flyway runs its DDL as
--  ADMIN with CURRENT_SCHEMA = TRAVEL_MATE, so the objects are owned by
--  TRAVEL_MATE.)

-- 2) Point the app at the schema (local: ORACLE_SCHEMA=TRAVEL_MATE in .env /
--    application-local.yml; prod: the deploy's environment). On first start
--    Flyway migrates V1+ into TRAVEL_MATE (a fresh schema -> fresh tables).

-- To verify after creation (as ADMIN):
--   SELECT username FROM all_users WHERE username = 'TRAVEL_MATE';
