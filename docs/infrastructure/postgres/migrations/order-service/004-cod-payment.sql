ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method varchar(20) NOT NULL DEFAULT 'ONLINE';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_payment_id bigint;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_paid_at timestamptz;

ALTER TABLE orders ADD CONSTRAINT ck_orders_cod_v1 CHECK (
    (payment_method='ONLINE' AND cod_payment_id IS NULL AND cod_paid_at IS NULL)
    OR (payment_method='COD' AND status IN ('CONFIRMED','CANCELLED','SHIPPING','COMPLETED')
        AND shipping_recipient IS NOT NULL AND shipping_phone IS NOT NULL AND shipping_street IS NOT NULL
        AND shipping_ward IS NOT NULL AND shipping_district IS NOT NULL AND shipping_province IS NOT NULL
        AND ((cod_payment_id IS NULL AND cod_paid_at IS NULL AND status<>'COMPLETED')
            OR (cod_payment_id IS NOT NULL AND cod_payment_id>0 AND cod_paid_at IS NOT NULL
                AND status IN ('SHIPPING','COMPLETED'))))
);
