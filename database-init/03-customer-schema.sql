\c customer_db;

CREATE SCHEMA IF NOT EXISTS customer;
SET search_path TO public;

DROP TABLE IF EXISTS point_reservations;
DROP TABLE IF EXISTS p_addresses;
DROP TABLE IF EXISTS p_customer;

CREATE TABLE IF NOT EXISTS p_customer (
    id           UUID PRIMARY KEY,
    name         VARCHAR(20) UNIQUE NOT NULL,
    nickname     VARCHAR(100),
    email        VARCHAR(255),
    password     VARCHAR(255) NOT NULL,
    phone_number VARCHAR(18),
    points       INTEGER,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    created_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by   VARCHAR(100) NOT NULL,
    deleted_at   TIMESTAMP,
    deleted_by   VARCHAR(100)
    );

CREATE TABLE IF NOT EXISTS p_addresses (
    id           UUID PRIMARY KEY,
    customer_id  UUID NOT NULL REFERENCES p_customer(id),
    zipcode      VARCHAR(10),
    road_addr    VARCHAR(500),
    detail_addr  VARCHAR(200),
    is_selected  BOOLEAN DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    created_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by   VARCHAR(100) NOT NULL,
    deleted_at   TIMESTAMP,
    deleted_by   VARCHAR(100)
    );
CREATE INDEX IF NOT EXISTS idx_addresses_customer ON p_addresses(customer_id);

CREATE TABLE IF NOT EXISTS point_reservations (
    reservation_id UUID PRIMARY KEY,
    customer_id    UUID NOT NULL,
    order_id       UUID NOT NULL,
    points         INTEGER NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    reserved_at    TIMESTAMP NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    created_by     VARCHAR(100) NOT NULL,
    updated_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_by     VARCHAR(100) NOT NULL,
    deleted_at     TIMESTAMP,
    deleted_by     VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_point_reservations_customer ON point_reservations(customer_id);
CREATE INDEX IF NOT EXISTS idx_point_reservations_order ON point_reservations(order_id);
CREATE INDEX IF NOT EXISTS idx_point_reservations_status ON point_reservations(status);
CREATE INDEX IF NOT EXISTS idx_point_reservations_customer_status ON point_reservations(customer_id, status);

