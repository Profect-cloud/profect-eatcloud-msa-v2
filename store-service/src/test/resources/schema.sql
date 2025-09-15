-- inventory 테이블
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS inventory_stock (
                                               menu_id       UUID PRIMARY KEY,
                                               is_unlimited  BOOLEAN NOT NULL DEFAULT FALSE,
                                               available_qty INTEGER NOT NULL DEFAULT 0,
                                               reserved_qty  INTEGER NOT NULL DEFAULT 0,
                                               updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS inventory_reservations (
                                                      reservation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                      order_line_id  UUID NOT NULL UNIQUE,
                                                      order_id       UUID,
                                                      menu_id        UUID NOT NULL,
                                                      quantity       INTEGER NOT NULL,
                                                      status         VARCHAR(16) NOT NULL, -- PENDING/CONFIRMED/CANCELED
                                                      expires_at     TIMESTAMP,
                                                      created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_inv_res_line ON inventory_reservations(order_line_id);
