-- Admin can disable/restore a user account (distinct from self-service deletion, which sets
-- IS_DELETED). A disabled account still appears in the admin panel but cannot log in or refresh.
-- Oracle has no native boolean: NUMBER(1) with a default so existing rows stay valid under NOT NULL.
ALTER TABLE USERS ADD (DISABLED NUMBER(1) DEFAULT 0 NOT NULL);
