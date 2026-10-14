-- Existing terminal notifications keep their original payment identity.
-- An unpaid expired order has no captured payment ID.
ALTER TABLE notification_logs ALTER COLUMN payment_id DROP NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname='ck_notification_payment_stage_v1'
          AND conrelid='notification_logs'::regclass
    ) THEN
        ALTER TABLE notification_logs ADD CONSTRAINT ck_notification_payment_stage_v1 CHECK (
            order_id>0 AND user_id>0 AND amount>0 AND
            ((event_type='order.expired' AND payment_id IS NULL) OR
             (event_type IN ('order.confirmed','order.cancelled') AND payment_id IS NOT NULL AND payment_id>0))
        ) NOT VALID;
        ALTER TABLE notification_logs VALIDATE CONSTRAINT ck_notification_payment_stage_v1;
    END IF;
END $$;
ALTER TABLE email_deliveries DROP CONSTRAINT IF EXISTS email_deliveries_event_type_check;
ALTER TABLE email_deliveries DROP CONSTRAINT IF EXISTS ck_email_delivery_stage;
ALTER TABLE email_deliveries ADD CONSTRAINT ck_email_delivery_stage_v2 CHECK (
    event_type IN ('order.confirmed','order.cancelled','order.expired','order.shipped','order.completed')
) NOT VALID;
ALTER TABLE email_deliveries VALIDATE CONSTRAINT ck_email_delivery_stage_v2;
