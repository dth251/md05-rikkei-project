DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN
        -- Hibernate update does not reliably extend an existing enum CHECK constraint.
        ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
        ALTER TABLE orders ADD CONSTRAINT ck_orders_status_v1
            CHECK (status IN ('PENDING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED')) NOT VALID;
        ALTER TABLE orders VALIDATE CONSTRAINT ck_orders_status_v1;
        CREATE INDEX IF NOT EXISTS idx_order_expiration ON orders(status,created_at);
    END IF;
END $$;
