-- Converge Hibernate-created fresh tables with existing SQL-created tables.
-- Never rewrite the checksums of migrations 002/003 already applied on VPS/local.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='returned_orders'::regclass
            AND conname='returned_orders_payment_id_fkey') THEN
        ALTER TABLE returned_orders ADD CONSTRAINT returned_orders_payment_id_fkey
            FOREIGN KEY (payment_id) REFERENCES payments(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='cash_refunds'::regclass
            AND conname='cash_refunds_payment_id_fkey') THEN
        ALTER TABLE cash_refunds ADD CONSTRAINT cash_refunds_payment_id_fkey
            FOREIGN KEY (payment_id) REFERENCES payments(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='returned_orders'::regclass
            AND conname='ck_returned_orders_identity_v1') THEN
        ALTER TABLE returned_orders ADD CONSTRAINT ck_returned_orders_identity_v1
            CHECK (order_id>0 AND user_id>0 AND amount>0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='cash_refunds'::regclass
            AND conname='ck_cash_refunds_identity_v1') THEN
        ALTER TABLE cash_refunds ADD CONSTRAINT ck_cash_refunds_identity_v1 CHECK (
            amount>0 AND refunded_by>0 AND length(trim(idempotency_key))>0 AND length(trim(receipt_number))>0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='cod_deliveries'::regclass
            AND conname='ck_cod_deliveries_identity_v1') THEN
        ALTER TABLE cod_deliveries ADD CONSTRAINT ck_cod_deliveries_identity_v1
            CHECK (order_id>0 AND user_id>0 AND amount>0 AND state IN ('SHIPPED','CANCELLED'));
    END IF;
END $$;
