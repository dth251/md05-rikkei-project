-- Hibernate may create tables first on an empty CI database. Apply the same
-- receipt/decision constraints without changing the checksummed migration 005.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='order_returns'::regclass
            AND conname='order_returns_order_id_fkey') THEN
        ALTER TABLE order_returns ADD CONSTRAINT order_returns_order_id_fkey
            FOREIGN KEY (order_id) REFERENCES orders(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='order_returns'::regclass
            AND conname='ck_order_returns_identity_v1') THEN
        ALTER TABLE order_returns ADD CONSTRAINT ck_order_returns_identity_v1 CHECK (
            order_id>0 AND user_id>0 AND amount>0 AND length(trim(idempotency_key))>0
            AND length(trim(reason))>0
            AND status IN ('REQUESTED','APPROVED','REJECTED','RECEIVED','REFUND_PENDING','REFUNDED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='order_returns'::regclass
            AND conname='ck_order_returns_decision_v1') THEN
        ALTER TABLE order_returns ADD CONSTRAINT ck_order_returns_decision_v1 CHECK (
            (status='REQUESTED' AND decided_by IS NULL AND decided_at IS NULL AND decision_note IS NULL)
            OR (status<>'REQUESTED' AND decided_by IS NOT NULL AND decided_by>0 AND decided_at IS NOT NULL
                AND decision_note IS NOT NULL AND length(trim(decision_note))>0));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='order_returns'::regclass
            AND conname='ck_order_returns_receipt_v1') THEN
        ALTER TABLE order_returns ADD CONSTRAINT ck_order_returns_receipt_v1 CHECK (
            (status IN ('REQUESTED','APPROVED','REJECTED') AND received_by IS NULL AND received_at IS NULL
                AND warehouse_receipt IS NULL AND restock IS NULL AND payment_id IS NULL AND refunded_at IS NULL)
            OR (status IN ('RECEIVED','REFUND_PENDING','REFUNDED') AND received_by IS NOT NULL AND received_by>0
                AND received_at IS NOT NULL AND warehouse_receipt IS NOT NULL AND length(trim(warehouse_receipt))>0
                AND restock IS NOT NULL AND (
                    (status='RECEIVED' AND payment_id IS NULL AND refunded_at IS NULL)
                    OR (status='REFUND_PENDING' AND payment_id IS NOT NULL AND payment_id>0 AND refunded_at IS NULL)
                    OR (status='REFUNDED' AND payment_id IS NOT NULL AND payment_id>0 AND refunded_at IS NOT NULL))));
    END IF;
END $$;
