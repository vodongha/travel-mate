-- A device records the UI language its owner chose (BCP-47 tag, e.g. "vi" / "en"); push
-- notifications are localized per device at send time. Null = follow the server default (English).
ALTER TABLE USER_DEVICES ADD (LOCALE VARCHAR2(10));
