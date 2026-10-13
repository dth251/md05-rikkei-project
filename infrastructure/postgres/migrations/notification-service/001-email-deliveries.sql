CREATE TABLE IF NOT EXISTS email_deliveries (
    delivery_id varchar(36) PRIMARY KEY,
    order_id bigint NOT NULL CHECK (order_id > 0),
    user_id bigint NOT NULL CHECK (user_id > 0),
    event_type varchar(40) NOT NULL CHECK (event_type IN ('order.confirmed','order.cancelled','order.shipped','order.completed')),
    state varchar(12) NOT NULL CHECK (state IN ('PENDING','SENDING','RETRY','SENT','FAILED','UNKNOWN')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    error_code varchar(40),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until timestamptz,
    sent_at timestamptz,
    CONSTRAINT uq_email_order_stage UNIQUE(order_id,event_type)
);
CREATE INDEX IF NOT EXISTS idx_email_delivery_due ON email_deliveries(next_attempt_at,created_at)
    WHERE state IN ('PENDING','RETRY');
