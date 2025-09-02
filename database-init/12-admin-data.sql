\c admin_db;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO p_store_categories (
    name, code, sort_order,
    is_active, total_store_amount,
    created_at, created_by, updated_at, updated_by
)
VALUES
    ('한식',   'KOREAN',  1, TRUE, 0, now(), 'system', now(), 'system'),
    ('분식',   'BUNSIK',  2, TRUE, 0, now(), 'system', now(), 'system'),
    ('중식',   'CHINESE', 3, TRUE, 0, now(), 'system', now(), 'system'),
    ('양식',   'WESTERN', 4, TRUE, 0, now(), 'system', now(), 'system')
ON CONFLICT (code) DO NOTHING;

INSERT INTO p_mid_categories (
    store_category_id, name, code, sort_order,
    is_active, total_store_amount,
    created_at, created_by, updated_at, updated_by
)
SELECT s.id, '밥류', 'RICE', 1,
       TRUE, 0, now(), 'system', now(), 'system'
FROM p_store_categories s
WHERE s.code = 'KOREAN'
ON CONFLICT (code) DO NOTHING;

INSERT INTO p_mid_categories (
    store_category_id, name, code, sort_order,
    is_active, total_store_amount,
    created_at, created_by, updated_at, updated_by
)
SELECT s.id, '면류', 'NOODLE', 2,
       TRUE, 0, now(), 'system', now(), 'system'
FROM p_store_categories s
WHERE s.code = 'KOREAN'
ON CONFLICT (code) DO NOTHING;

INSERT INTO p_mid_categories (
    store_category_id, name, code, sort_order,
    is_active, total_store_amount,
    created_at, created_by, updated_at, updated_by
)
SELECT s.id, '볶음류', 'FRIED', 1,
       TRUE, 0, now(), 'system', now(), 'system'
FROM p_store_categories s
WHERE s.code = 'CHINESE'
ON CONFLICT (code) DO NOTHING;

INSERT INTO p_menu_categories (
    store_category_id, mid_category_id, name, code, sort_order,
    is_active, total_store_amount,
    created_at, created_by, updated_at, updated_by
)
SELECT s.id, m.id, '비빔밥', 'BIBIMBAP', 1,
       TRUE, 0, now(), 'system', now(), 'system'
FROM p_store_categories s
         JOIN p_mid_categories m ON m.code = 'RICE'
WHERE s.code = 'KOREAN'
ON CONFLICT (code) DO NOTHING;

INSERT INTO p_menu_categories (
    store_category_id, mid_category_id, name, code, sort_order,
    is_active, total_store_amount,
    created_at, created_by, updated_at, updated_by
)
SELECT s.id, m.id, '칼국수', 'KALGUKSU', 2,
       TRUE, 0, now(), 'system', now(), 'system'
FROM p_store_categories s
         JOIN p_mid_categories m ON m.code = 'NOODLE'
WHERE s.code = 'KOREAN'
ON CONFLICT (code) DO NOTHING;

INSERT INTO p_menu_categories (
    store_category_id, mid_category_id, name, code, sort_order,
    is_active, total_store_amount,
    created_at, created_by, updated_at, updated_by
)
SELECT s.id, m.id, '짜장면', 'JJAJANG', 1,
       TRUE, 0, now(), 'system', now(), 'system'
FROM p_store_categories s
         JOIN p_mid_categories m ON m.code = 'FRIED'
WHERE s.code = 'CHINESE'
ON CONFLICT (code) DO NOTHING;

INSERT INTO p_admins (
    id, name, email, password, phone_number, position,
    created_at, created_by, updated_at, updated_by
)
VALUES
    (gen_random_uuid(), '관리자1', 'admin1@example.com', '$2a$12$hbYaL8KjbojlMgLvoXHdh.zXyOOkH07eYiXsJAmTDYEJ3IvPb45xa', '010-1111-2222', 'MASTER',
     now(), 'system', now(), 'system'),
    (gen_random_uuid(), '관리자2', 'admin2@example.com', '$2a$12$6YsQY5C/14fh07vnQHwiT.2L/064QAmDdOELX335C1NwXb0pSuXhC', '010-3333-4444', 'REVIEWER',
     now(), 'system', now(), 'system')
ON CONFLICT (email) DO NOTHING;

