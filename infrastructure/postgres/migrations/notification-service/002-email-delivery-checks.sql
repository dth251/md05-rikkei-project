-- Enforce the same bounds when the table already existed through Hibernate ddl-auto=update.
DO $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='ck_email_delivery_identity' AND conrelid='email_deliveries'::regclass) THEN
        ALTER TABLE email_deliveries ADD CONSTRAINT ck_email_delivery_identity CHECK(order_id>0 AND user_id>0 AND attempts>=0);
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='ck_email_delivery_state' AND conrelid='email_deliveries'::regclass) THEN
        ALTER TABLE email_deliveries ADD CONSTRAINT ck_email_delivery_state CHECK(state IN ('PENDING','SENDING','RETRY','SENT','FAILED','UNKNOWN'));
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='ck_email_delivery_stage' AND conrelid='email_deliveries'::regclass) THEN
        ALTER TABLE email_deliveries ADD CONSTRAINT ck_email_delivery_stage CHECK(event_type IN ('order.confirmed','order.cancelled','order.shipped','order.completed'));
    END IF;
END $$;
