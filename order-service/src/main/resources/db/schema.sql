DROP TABLE IF EXISTS p_reviews CASCADE;
DROP TABLE IF EXISTS p_delivery_orders CASCADE;
DROP TABLE IF EXISTS p_pickup_orders CASCADE;
DROP TABLE IF EXISTS p_orders CASCADE;
DROP TABLE IF EXISTS p_cart CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;
DROP TABLE IF EXISTS order_type_codes CASCADE;
DROP TABLE IF EXISTS order_status_codes CASCADE;

CREATE TABLE IF NOT EXISTS p_cart (
    cart_id     UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    cart_items  JSONB NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    created_by  VARCHAR(100) NOT NULL,
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by  VARCHAR(100) NOT NULL,
    deleted_at  TIMESTAMP,
    deleted_by  VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS p_orders (
                                        order_id              UUID PRIMARY KEY,
                                        order_number          VARCHAR(50) UNIQUE NOT NULL,
    customer_id           UUID NOT NULL,
    store_id              UUID NOT NULL,
    payment_id            UUID,
    order_status          VARCHAR(30) NOT NULL,
    order_type            VARCHAR(30) NOT NULL,
    order_menu_list       JSONB NOT NULL,
    total_price           INTEGER NOT NULL DEFAULT 0,
    use_points            BOOLEAN NOT NULL DEFAULT FALSE,
    points_to_use         INTEGER NOT NULL DEFAULT 0,
    final_payment_amount  INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    created_by            VARCHAR(100) NOT NULL,
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by            VARCHAR(100) NOT NULL,
    deleted_at            TIMESTAMP,
    deleted_by            VARCHAR(100)
    );

CREATE TABLE IF NOT EXISTS p_delivery_orders (
                                                 order_id                   UUID PRIMARY KEY REFERENCES p_orders(order_id),
    delivery_fee               NUMERIC(10,2) DEFAULT 0,
    delivery_requests          TEXT,
    zipcode                    VARCHAR(10),
    road_addr                  VARCHAR(500),
    detail_addr                VARCHAR(200),
    estimated_delivery_time    TIMESTAMP,
    estimated_preparation_time INTEGER,
    canceled_at                TIMESTAMP,
    canceled_by                VARCHAR(100),
    cancel_reason              TEXT,
    created_at                 TIMESTAMP    NOT NULL DEFAULT now(),
    created_by                 VARCHAR(100) NOT NULL,
    updated_at                 TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by                 VARCHAR(100) NOT NULL,
    deleted_at                 TIMESTAMP,
    deleted_by                 VARCHAR(100)
    );

CREATE TABLE IF NOT EXISTS p_pickup_orders (
                                               order_id              UUID PRIMARY KEY REFERENCES p_orders(order_id),
    pickup_requests       TEXT,
    estimated_pickup_time TIMESTAMP,
    canceled_at           TIMESTAMP,
    canceled_by           VARCHAR(100),
    cancel_reason         TEXT,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    created_by            VARCHAR(100) NOT NULL,
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by            VARCHAR(100) NOT NULL,
    deleted_at            TIMESTAMP,
    deleted_by            VARCHAR(100)
    );

CREATE TABLE IF NOT EXISTS p_reviews (
                                         review_id  UUID PRIMARY KEY,
                                         order_id   UUID NOT NULL REFERENCES p_orders(order_id),
    rating     NUMERIC(2,1) NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100)
    );

-- 주문 상태 코드 테이블
CREATE TABLE IF NOT EXISTS order_status_codes (
    code VARCHAR(30) PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100)
);

-- Outbox events table
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    next_attempt_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status);
CREATE INDEX IF NOT EXISTS idx_outbox_next_attempt_at ON outbox_events(next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);

CREATE TABLE IF NOT EXISTS order_type_codes (
    code VARCHAR(30) PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_by VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100)
);
