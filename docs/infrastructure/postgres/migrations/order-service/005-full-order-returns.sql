CREATE TABLE IF NOT EXISTS order_returns (
    order_id bigint PRIMARY KEY REFERENCES orders(id),
    user_id bigint NOT NULL CHECK (user_id>0),
    amount numeric(18,2) NOT NULL CHECK (amount>0),
    idempotency_key varchar(128) NOT NULL CHECK (length(trim(idempotency_key))>0),
    reason varchar(500) NOT NULL CHECK (length(trim(reason))>0),
    status varchar(24) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','REJECTED','RECEIVED','REFUND_PENDING','REFUNDED')),
    requested_at timestamptz NOT NULL,
    decided_by bigint,
    decided_at timestamptz,
    decision_note varchar(500),
    received_by bigint,
    received_at timestamptz,
    warehouse_receipt varchar(128) UNIQUE,
    restock boolean,
    payment_id bigint,
    refunded_at timestamptz,
    CONSTRAINT ck_order_returns_decision_v1 CHECK (
        (status='REQUESTED' AND decided_by IS NULL AND decided_at IS NULL AND decision_note IS NULL)
        OR (status<>'REQUESTED' AND decided_by IS NOT NULL AND decided_by>0 AND decided_at IS NOT NULL
            AND decision_note IS NOT NULL AND length(trim(decision_note))>0)
    ),
    CONSTRAINT ck_order_returns_receipt_v1 CHECK (
        (status IN ('REQUESTED','APPROVED','REJECTED') AND received_by IS NULL AND received_at IS NULL
            AND warehouse_receipt IS NULL AND restock IS NULL AND payment_id IS NULL AND refunded_at IS NULL)
        OR (status IN ('RECEIVED','REFUND_PENDING','REFUNDED') AND received_by IS NOT NULL AND received_by>0
            AND received_at IS NOT NULL AND warehouse_receipt IS NOT NULL AND length(trim(warehouse_receipt))>0
            AND restock IS NOT NULL AND (
                (status='RECEIVED' AND payment_id IS NULL AND refunded_at IS NULL)
                OR (status='REFUND_PENDING' AND payment_id IS NOT NULL AND payment_id>0 AND refunded_at IS NULL)
                OR (status='REFUNDED' AND payment_id IS NOT NULL AND payment_id>0 AND refunded_at IS NOT NULL)))
    )
);
