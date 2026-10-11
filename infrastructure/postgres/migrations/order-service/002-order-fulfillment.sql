DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
        ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_status_v1;
        ALTER TABLE orders ADD CONSTRAINT ck_orders_status_v2 CHECK (
            status IN ('PENDING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED','SHIPPING','COMPLETED')
        ) NOT VALID;
        ALTER TABLE orders VALIDATE CONSTRAINT ck_orders_status_v2;
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_recipient varchar(100);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_phone varchar(20);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_street varchar(255);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_ward varchar(100);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_district varchar(100);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_province varchar(100);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_postal_code varchar(20);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS carrier varchar(100);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_number varchar(128);
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipped_at timestamptz;
        ALTER TABLE orders ADD COLUMN IF NOT EXISTS completed_at timestamptz;
        ALTER TABLE orders ADD CONSTRAINT ck_order_fulfillment_v1 CHECK (
            (status NOT IN ('SHIPPING','COMPLETED') AND carrier IS NULL AND tracking_number IS NULL
                AND shipped_at IS NULL AND completed_at IS NULL)
            OR (status IN ('SHIPPING','COMPLETED') AND carrier IS NOT NULL AND tracking_number IS NOT NULL
                AND shipped_at IS NOT NULL AND shipping_recipient IS NOT NULL AND shipping_phone IS NOT NULL
                AND shipping_street IS NOT NULL AND shipping_ward IS NOT NULL AND shipping_district IS NOT NULL
                AND shipping_province IS NOT NULL AND ((status='SHIPPING' AND completed_at IS NULL)
                    OR (status='COMPLETED' AND completed_at IS NOT NULL AND completed_at>=shipped_at)))
        ) NOT VALID;
        ALTER TABLE orders VALIDATE CONSTRAINT ck_order_fulfillment_v1;
    END IF;
END $$;
