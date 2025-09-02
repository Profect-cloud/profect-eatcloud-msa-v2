\c manager_db;

CREATE TABLE IF NOT EXISTS p_managers (
    id           UUID PRIMARY KEY,
    name     VARCHAR(20)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    phone_number VARCHAR(18),
    position     VARCHAR(50),

    -- audit & soft-delete (auto-time BaseTimeEntity와 매핑)
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    created_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by   VARCHAR(100) NOT NULL,
    deleted_at   TIMESTAMP,
    deleted_by   VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_p_managers_deleted_at ON p_managers(deleted_at);
