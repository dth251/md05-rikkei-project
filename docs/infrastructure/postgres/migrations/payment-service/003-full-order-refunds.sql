ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_cod_v1;
ALTER TABLE payments ADD CONSTRAINT ck_payments_cod_v2 CHECK (
    (payment_method='ONLINE' AND cod_idempotency_key IS NULL AND cod_receipt_number IS NULL
        AND cod_collected_by IS NULL AND cod_collected_at IS NULL)
    OR (payment_method='COD' AND reservation_expired=false
        AND ((status IN ('PENDING','FAILED') AND refund_status='NONE'
            AND cod_idempotency_key IS NULL AND cod_receipt_number IS NULL
            AND cod_collected_by IS NULL AND cod_collected_at IS NULL)
        OR (status='SUCCESS' AND refund_status IN ('NONE','REQUIRED','REFUNDED')
            AND cod_idempotency_key IS NOT NULL AND length(trim(cod_idempotency_key))>0
            AND cod_receipt_number IS NOT NULL AND length(trim(cod_receipt_number))>0
            AND cod_collected_by IS NOT NULL AND cod_collected_by>0 AND cod_collected_at IS NOT NULL
            AND completed_at IS NOT NULL AND completed_at=cod_collected_at)))
);

CREATE TABLE IF NOT EXISTS returned_orders (
    order_id bigint PRIMARY KEY CHECK (order_id>0),
    user_id bigint NOT NULL CHECK (user_id>0),
    amount numeric(18,2) NOT NULL CHECK (amount>0),
    received_at timestamptz NOT NULL,
    payment_id bigint NOT NULL UNIQUE REFERENCES payments(id),
    refund_ready_at timestamptz,
    refund_notified_at timestamptz
);

CREATE TABLE IF NOT EXISTS cash_refunds (
    payment_id bigint PRIMARY KEY REFERENCES payments(id),
    amount numeric(18,2) NOT NULL CHECK (amount>0),
    idempotency_key varchar(128) NOT NULL UNIQUE CHECK (length(trim(idempotency_key))>0),
    receipt_number varchar(128) NOT NULL UNIQUE CHECK (length(trim(receipt_number))>0),
    refunded_by bigint NOT NULL CHECK (refunded_by>0),
    refunded_at timestamptz NOT NULL
);
