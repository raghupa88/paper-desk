-- Daily usage streak: tracked per user (real calendar days), independent of
-- any single scenario account — this is a habit-forming mechanic, not a
-- trading-performance one (see the existing HOT_STREAK_5 achievement for that).

ALTER TABLE users ADD last_active_date DATE;
ALTER TABLE users ADD current_streak NUMBER(6) DEFAULT 0 NOT NULL;
ALTER TABLE users ADD longest_streak NUMBER(6) DEFAULT 0 NOT NULL;
