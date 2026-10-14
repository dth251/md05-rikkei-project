DO $$ BEGIN
    IF to_regclass('public.outbox_events') IS NOT NULL THEN
        ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS trace_parent varchar(55);
    END IF;
END $$;
