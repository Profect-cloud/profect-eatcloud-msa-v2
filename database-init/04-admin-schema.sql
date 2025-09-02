\c admin_db;


SET search_path TO public;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS p_store_categories (
  id                 INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name               VARCHAR(100) NOT NULL UNIQUE,
  code               VARCHAR(50)  NOT NULL UNIQUE, -- immutable slug (e.g., KOREAN)
  sort_order         INT          NOT NULL DEFAULT 0,
  is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
  total_store_amount INT          NOT NULL DEFAULT 0, -- number of stores using this category
  created_at         TIMESTAMP    NOT NULL DEFAULT now(),
  updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
  created_by         VARCHAR(100) NOT NULL,
  updated_by         VARCHAR(100) NOT NULL,
  deleted_at         TIMESTAMP,
  deleted_by         VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_storecat_active_sort
  ON p_store_categories (is_active, sort_order, id);

CREATE TABLE IF NOT EXISTS p_mid_categories (
  id                 INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  store_category_id  INT NOT NULL REFERENCES p_store_categories(id),
  name               VARCHAR(100) NOT NULL,
  code               VARCHAR(100) NOT NULL UNIQUE, -- e.g., RICE, NOODLE
  sort_order         INT          NOT NULL DEFAULT 0,
  is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
  total_store_amount INT          NOT NULL DEFAULT 0,
  created_at         TIMESTAMP    NOT NULL DEFAULT now(),
  updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
  created_by         VARCHAR(100) NOT NULL,
  updated_by         VARCHAR(100) NOT NULL,
  deleted_at         TIMESTAMP,
  deleted_by         VARCHAR(100),
  CONSTRAINT uq_mid_name_in_store UNIQUE (store_category_id, name)
);
CREATE INDEX IF NOT EXISTS idx_mid_store ON p_mid_categories (store_category_id);
CREATE INDEX IF NOT EXISTS idx_mid_active_sort_in_cat
  ON p_mid_categories (store_category_id, is_active, sort_order, id);

CREATE TABLE IF NOT EXISTS p_menu_categories (
  id                 INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  store_category_id  INT NOT NULL REFERENCES p_store_categories(id), -- denormalized for fast filters
  mid_category_id    INT NOT NULL REFERENCES p_mid_categories(id),
  name               VARCHAR(100) NOT NULL,
  code               VARCHAR(100) NOT NULL UNIQUE, -- e.g., BIBIMBAP
  sort_order         INT          NOT NULL DEFAULT 0,
  is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
  total_store_amount INT          NOT NULL DEFAULT 0,
  created_at         TIMESTAMP    NOT NULL DEFAULT now(),
  updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
  created_by         VARCHAR(100) NOT NULL,
  updated_by         VARCHAR(100) NOT NULL,
  deleted_at         TIMESTAMP,
  deleted_by         VARCHAR(100),
  CONSTRAINT uq_menu_name_in_mid UNIQUE (mid_category_id, name)
);
CREATE INDEX IF NOT EXISTS idx_menu_mid ON p_menu_categories (mid_category_id);
CREATE INDEX IF NOT EXISTS idx_menu_store ON p_menu_categories (store_category_id);
CREATE INDEX IF NOT EXISTS idx_menu_active_sort_in_mid
  ON p_menu_categories (mid_category_id, is_active, sort_order, id);

CREATE TABLE IF NOT EXISTS p_admins (
  id           UUID PRIMARY KEY,
  name         VARCHAR(20) UNIQUE NOT NULL,
  email        VARCHAR(255) UNIQUE NOT NULL,
  password     VARCHAR(255)       NOT NULL,
  phone_number VARCHAR(18),
  position     VARCHAR(50),
  created_at   TIMESTAMP    NOT NULL DEFAULT now(),
  created_by   VARCHAR(100) NOT NULL,
  updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
  updated_by   VARCHAR(100) NOT NULL,
  deleted_at   TIMESTAMP,
  deleted_by   VARCHAR(100)
);

-- CREATE TABLE IF NOT EXISTS p_manager_store_applications (
--     application_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--
--     manager_name         VARCHAR(20)  NOT NULL,
--     manager_email        VARCHAR(255) NOT NULL,
--     manager_password     VARCHAR(255) NOT NULL,
--     manager_phone_number VARCHAR(18),
--
--     store_name           VARCHAR(200) NOT NULL,
--     store_address        VARCHAR(300),
--     store_phone_number   VARCHAR(18),
--     store_category_id    INT NOT NULL REFERENCES p_store_categories(id),
--     description          TEXT,
--
--     status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING|APPROVED|REJECTED
--     reviewer_admin_id    UUID REFERENCES p_admins(id) ON DELETE SET NULL,
--     review_comment       TEXT,
--
--     created_at           TIMESTAMP    NOT NULL DEFAULT now(),
--     created_by           VARCHAR(100) NOT NULL DEFAULT 'system',
--     updated_at           TIMESTAMP    NOT NULL DEFAULT now(),
--     updated_by           VARCHAR(100) NOT NULL DEFAULT 'system',
--     deleted_at           TIMESTAMP,
--     deleted_by           VARCHAR(100)
-- );

-- CREATE UNIQUE INDEX IF NOT EXISTS ux_mgrstore_manager_email
--     ON p_manager_store_applications (manager_email);
--
-- CREATE INDEX IF NOT EXISTS idx_mgrstore_status
--     ON p_manager_store_applications (status);
--
-- CREATE INDEX IF NOT EXISTS idx_mgrstore_store_category
--     ON p_manager_store_applications (store_category_id);
--
-- CREATE INDEX IF NOT EXISTS idx_mgrstore_reviewer
--     ON p_manager_store_applications (reviewer_admin_id);

