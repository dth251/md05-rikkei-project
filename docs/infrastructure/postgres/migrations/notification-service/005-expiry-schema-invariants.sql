-- Append-only upgrade: expiry is terminal even if Payment never created a row.
ALTER TABLE notification_logs ALTER COLUMN payment_id DROP NOT NULL;
DO $$
DECLARE item record;
BEGIN
    FOR item IN SELECT conname FROM pg_constraint
        WHERE conrelid='notification_logs'::regclass AND contype='c'
            AND pg_get_constraintdef(oid) LIKE '%payment_id%'
    LOOP
        EXECUTE format('ALTER TABLE notification_logs DROP CONSTRAINT %I', item.conname);
    END LOOP;
    ALTER TABLE notification_logs ADD CONSTRAINT ck_notification_terminal_identity CHECK (
        order_id>0 AND user_id>0 AND amount>0 AND (
            (event_type='order.expired' AND payment_id IS NULL)
            OR (event_type IN ('order.confirmed','order.cancelled') AND payment_id IS NOT NULL AND payment_id>0)));

    FOR item IN SELECT conname FROM pg_constraint
        WHERE conrelid='email_deliveries'::regclass AND contype='c'
            AND pg_get_constraintdef(oid) LIKE '%event_type%'
    LOOP
        EXECUTE format('ALTER TABLE email_deliveries DROP CONSTRAINT %I', item.conname);
    END LOOP;
    ALTER TABLE email_deliveries ADD CONSTRAINT ck_email_delivery_stage CHECK (
        event_type IN ('order.confirmed','order.cancelled','order.expired','order.shipped','order.completed'));
END $$;
