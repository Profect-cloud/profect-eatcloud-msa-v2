DROP TABLE IF EXISTS p_payments CASCADE;
DROP TABLE IF EXISTS p_payment_requests CASCADE;
DROP TABLE IF EXISTS payment_status_codes CASCADE;
DROP TABLE IF EXISTS payment_method_codes CASCADE;

CREATE TABLE payment_method_codes (
  code         VARCHAR(30) PRIMARY KEY,
  display_name VARCHAR(50) NOT NULL,
  sort_order   INTEGER NOT NULL,
  is_active    BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMP    NOT NULL DEFAULT now(),
  created_by   VARCHAR(100) NOT NULL,
  updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by   VARCHAR(100) NOT NULL,
  deleted_at   TIMESTAMP,
  deleted_by   VARCHAR(100)
);

CREATE TABLE payment_status_codes (
  code         VARCHAR(30) PRIMARY KEY,
  display_name VARCHAR(50) NOT NULL,
  sort_order   INTEGER NOT NULL,
  is_active    BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMP    NOT NULL DEFAULT now(),
  created_by   VARCHAR(100) NOT NULL,
  updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by   VARCHAR(100) NOT NULL,
  deleted_at   TIMESTAMP,
  deleted_by   VARCHAR(100)
);

CREATE TABLE p_payment_requests (
    payment_request_id UUID PRIMARY KEY,
    order_id           UUID NOT NULL,
    customer_id        UUID NOT NULL,
    pg_provider        VARCHAR(100) NOT NULL,
    request_payload    JSONB NOT NULL,
    status             VARCHAR(50) NOT NULL,
    requested_at       TIMESTAMP NOT NULL,
    responded_at       TIMESTAMP,
    failure_reason     TEXT,
    redirect_url       TEXT,
    timeout_at         TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    created_by         VARCHAR(100) NOT NULL,
    updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by         VARCHAR(100) NOT NULL,
    deleted_at         TIMESTAMP,
    deleted_by         VARCHAR(100)
);

CREATE TABLE p_payments (
    payment_id         UUID PRIMARY KEY,
    order_id           UUID NOT NULL,
    customer_id        UUID NOT NULL,
    payment_status     VARCHAR(30) NOT NULL REFERENCES payment_status_codes(code),
    payment_method     VARCHAR(30) NOT NULL REFERENCES payment_method_codes(code),
    total_amount       INTEGER NOT NULL,
    pg_transaction_id  VARCHAR(100),
    approval_code      VARCHAR(50),
    card_info          JSONB,
    redirect_url       TEXT,
    receipt_url        TEXT,
    requested_at       TIMESTAMP NOT NULL,
    approved_at        TIMESTAMP,
    failed_at          TIMESTAMP,
    failure_reason     TEXT,
    offline_payment_note TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    created_by         VARCHAR(100) NOT NULL,
    updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by         VARCHAR(100) NOT NULL,
    deleted_at         TIMESTAMP,
    deleted_by         VARCHAR(100)
);
