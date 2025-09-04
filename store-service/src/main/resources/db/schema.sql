
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS p_stores (
  store_id         UUID PRIMARY KEY,
  store_name       VARCHAR(200) NOT NULL UNIQUE,
  store_address    VARCHAR(300),
  phone_number     VARCHAR(18),
  store_category_id INT NOT NULL,
  manager_id       UUID,
  min_cost         INTEGER NOT NULL DEFAULT 0,
  description      TEXT,
  store_lat        DOUBLE PRECISION,
  store_lon        DOUBLE PRECISION,
  open_status      BOOLEAN,
  open_time        TIME NOT NULL,
  close_time       TIME NOT NULL,
  location         geography(Point, 4326),

  -- ⭐ Ratings (denormalized)
  rating_sum       NUMERIC(10,2) NOT NULL DEFAULT 0,
  rating_count     INTEGER       NOT NULL DEFAULT 0,
  avg_rating       NUMERIC(3,2)  NOT NULL DEFAULT 0,


  created_at       TIMESTAMP     NOT NULL DEFAULT now(),
  created_by       VARCHAR(100)  NOT NULL,
  updated_at       TIMESTAMP     NOT NULL DEFAULT now(),
  updated_by       VARCHAR(100)  NOT NULL,
  deleted_at       TIMESTAMP,
  deleted_by       VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_stores_rating
  ON p_stores (avg_rating DESC, rating_count DESC);

CREATE TABLE IF NOT EXISTS p_menus (
  menu_id            UUID PRIMARY KEY,
  store_id           UUID NOT NULL REFERENCES p_stores(store_id),
  menu_num           INTEGER NOT NULL,
  menu_name          VARCHAR(200) NOT NULL,
  menu_category_code VARCHAR(100) NOT NULL,
  price              INTEGER NOT NULL,
  description        TEXT,
  is_available       BOOLEAN NOT NULL DEFAULT TRUE,
  image_url          VARCHAR(500),


  is_unlimited       BOOLEAN NOT NULL DEFAULT FALSE,
  stock_quantity     INTEGER NOT NULL DEFAULT 0,

  created_at         TIMESTAMP    NOT NULL DEFAULT now(),
  created_by         VARCHAR(100) NOT NULL,
  updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by         VARCHAR(100) NOT NULL,
  deleted_at         TIMESTAMP,
  deleted_by         VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_menus_store_category
  ON p_menus (store_id, menu_category_code);

CREATE INDEX IF NOT EXISTS idx_menus_stock
  ON p_menus (store_id, is_available, is_unlimited, stock_quantity);

CREATE TABLE IF NOT EXISTS menu_vectors (
  id BIGSERIAL PRIMARY KEY,
  menu_name VARCHAR(255) NOT NULL UNIQUE,
  tfidf_vector JSON,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_menu_vectors_name
  ON menu_vectors (menu_name);

CREATE TABLE IF NOT EXISTS delivery_areas (
  area_id     UUID PRIMARY KEY,
  area_name   VARCHAR(100) NOT NULL,
  created_at  TIMESTAMP    NOT NULL DEFAULT now(),
  created_by  VARCHAR(100) NOT NULL,
  updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by  VARCHAR(100) NOT NULL,
  deleted_at  TIMESTAMP,
  deleted_by  VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS p_store_delivery_areas (
  store_id     UUID NOT NULL REFERENCES p_stores(store_id),
  area_id      UUID NOT NULL REFERENCES delivery_areas(area_id),
  delivery_fee INTEGER NOT NULL DEFAULT 0,
  created_at   TIMESTAMP    NOT NULL DEFAULT now(),
  created_by   VARCHAR(100) NOT NULL,
  updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by   VARCHAR(100) NOT NULL,
  deleted_at   TIMESTAMP,
  deleted_by   VARCHAR(100),
  PRIMARY KEY (store_id, area_id)
);


CREATE TABLE IF NOT EXISTS daily_store_sales (
  sale_date    DATE NOT NULL,
  store_id     UUID NOT NULL,
  order_count  INTEGER NOT NULL,
  total_amount NUMERIC(12,2) NOT NULL,
  created_at   TIMESTAMP    NOT NULL DEFAULT now(),
  created_by   VARCHAR(100) NOT NULL,
  updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by   VARCHAR(100) NOT NULL,
  deleted_at   TIMESTAMP,
  deleted_by   VARCHAR(100),
  PRIMARY KEY (sale_date, store_id)
);
CREATE INDEX IF NOT EXISTS idx_daily_store_sales_store
  ON daily_store_sales (store_id);

CREATE TABLE IF NOT EXISTS daily_menu_sales (
  sale_date     DATE NOT NULL,
  store_id      UUID NOT NULL,
  menu_id       UUID NOT NULL,
  quantity_sold INTEGER NOT NULL,
  total_amount  NUMERIC(12,2) NOT NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT now(),
  created_by    VARCHAR(100) NOT NULL,
  updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by    VARCHAR(100) NOT NULL,
  deleted_at    TIMESTAMP,
  deleted_by    VARCHAR(100),
  PRIMARY KEY (sale_date, store_id, menu_id)
);
CREATE INDEX IF NOT EXISTS idx_daily_menu_sales_store_date
  ON daily_menu_sales (store_id, sale_date);


CREATE TABLE IF NOT EXISTS p_ai_responses (
  ai_response_id UUID PRIMARY KEY,
  description    TEXT NOT NULL,
  created_at     TIMESTAMP    NOT NULL DEFAULT now(),
  created_by     VARCHAR(100) NOT NULL,
  updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by     VARCHAR(100) NOT NULL,
  deleted_at     TIMESTAMP,
  deleted_by     VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS p_stock_logs (
    log_id           UUID PRIMARY KEY,
    menu_id          UUID NOT NULL,
    order_id         UUID,
    order_line_id    UUID,
    action           VARCHAR(16) NOT NULL,   -- RESERVE, CONFIRM, CANCEL
    change_amount    INT NOT NULL,           -- -N, 0, +N
    reason           VARCHAR(100),
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_logs_line_action
    ON p_stock_logs(order_line_id, action);

CREATE INDEX IF NOT EXISTS idx_stock_logs_menu
    ON p_stock_logs(menu_id);
