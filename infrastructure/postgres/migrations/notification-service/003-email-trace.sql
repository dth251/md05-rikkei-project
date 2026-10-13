ALTER TABLE email_deliveries ADD COLUMN IF NOT EXISTS trace_parent varchar(55);
