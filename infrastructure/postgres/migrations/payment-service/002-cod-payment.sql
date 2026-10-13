ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_method varchar(20) NOT NULL DEFAULT 'ONLINE';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cod_idempotency_key varchar(128);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cod_receipt_number varchar(128);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cod_collected_by bigint;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cod_collected_at timestamptz;
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_cod_idempotency ON payments(cod_idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_cod_receipt ON payments(cod_receipt_number);

ALTER TABLE payments ADD CONSTRAINT ck_payments_cod_v1 CHECK (
    (payment_method='ONLINE' AND cod_idempotency_key IS NULL AND cod_receipt_number IS NULL
        AND cod_collected_by IS NULL AND cod_collected_at IS NULL)
    OR (payment_method='COD' AND reservation_expired=false AND refund_status='NONE'
        AND ((status IN ('PENDING','FAILED') AND cod_idempotency_key IS NULL AND cod_receipt_number IS NULL
            AND cod_collected_by IS NULL AND cod_collected_at IS NULL)
        OR (status='SUCCESS' AND cod_idempotency_key IS NOT NULL AND length(trim(cod_idempotency_key))>0
            AND cod_receipt_number IS NOT NULL AND length(trim(cod_receipt_number))>0
            AND cod_collected_by IS NOT NULL AND cod_collected_by>0 AND cod_collected_at IS NOT NULL
            AND completed_at IS NOT NULL AND completed_at=cod_collected_at)))
);

CREATE TABLE IF NOT EXISTS cod_deliveries (
    order_id bigint PRIMARY KEY CHECK (order_id>0),
    user_id bigint NOT NULL CHECK (user_id>0),
    amount numeric(18,2) NOT NULL CHECK (amount>0),
    state varchar(16) NOT NULL CHECK (state IN ('SHIPPED','CANCELLED')),
    occurred_at timestamptz NOT NULL
);
