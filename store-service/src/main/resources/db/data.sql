CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS postgis;
SET search_path TO public;

-- 테스트용 가게 데이터 (개별 INSERT로 분리)
INSERT INTO p_stores (
    store_id, manager_id,
    store_name, store_address, phone_number,
    store_category_id, min_cost, description, store_lat, store_lon,
    open_status, open_time, close_time, location,
    rating_sum, rating_count, avg_rating,
    created_at, created_by, updated_at, updated_by
) VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
    '길동네 한식당', '서울 종로구 1번지', '02-111-2222',
    1, 10000, '정통 한식 전문점입니다.', 37.5725, 126.9769,
    TRUE, '10:00', '22:00',
    ST_SetSRID(ST_MakePoint(126.9769, 37.5725), 4326)::geography,
    0, 0, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (store_name) DO NOTHING;

INSERT INTO p_stores (
    store_id, manager_id,
    store_name, store_address, phone_number,
    store_category_id, min_cost, description, store_lat, store_lon,
    open_status, open_time, close_time, location,
    rating_sum, rating_count, avg_rating,
    created_at, created_by, updated_at, updated_by
) VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
    '맛있는 치킨', '서울 강남구 3번지', '02-555-6666',
    3, 15000, '치킨 전문점, 다양한 치킨 메뉴', 37.5000, 127.0000,
    TRUE, '11:00', '24:00',
    ST_SetSRID(ST_MakePoint(127.0000, 37.5000), 4326)::geography,
    0, 0, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (store_name) DO NOTHING;

INSERT INTO p_stores (
    store_id, manager_id,
    store_name, store_address, phone_number,
    store_category_id, min_cost, description, store_lat, store_lon,
    open_status, open_time, close_time, location,
    rating_sum, rating_count, avg_rating,
    created_at, created_by, updated_at, updated_by
) VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd'::uuid, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
    '버거 하우스', '서울 서초구 4번지', '02-777-8888',
    5, 12000, '햄버거 전문점, 다양한 버거 메뉴', 37.4800, 127.0200,
    TRUE, '10:00', '23:00',
    ST_SetSRID(ST_MakePoint(127.0200, 37.4800), 4326)::geography,
    0, 0, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (store_name) DO NOTHING;

-- 테스트용 메뉴 데이터 (개별 INSERT로 분리)
INSERT INTO p_menus (
    menu_id, store_id, menu_num, menu_name, menu_category_code, price, description, is_available, image_url, is_unlimited, stock_quantity,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '11111111-1111-1111-1111-111111111111'::uuid, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 1,
    '김치찌개', 'KOREAN', 8000, '진짜 맛있는 김치찌개', true, null, false, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO p_menus (
    menu_id, store_id, menu_num, menu_name, menu_category_code, price, description, is_available, image_url, is_unlimited, stock_quantity,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '11111111-1111-1111-1111-111111111112'::uuid, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 2,
    '된장찌개', 'KOREAN', 7000, '구수한 된장찌개', true, null, false, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO p_menus (
    menu_id, store_id, menu_num, menu_name, menu_category_code, price, description, is_available, image_url, is_unlimited, stock_quantity,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '11111111-1111-1111-1111-111111111113'::uuid, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid, 3,
    '불고기', 'KOREAN', 15000, '양념이 일품인 불고기', true, null, false, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO p_menus (
    menu_id, store_id, menu_num, menu_name, menu_category_code, price, description, is_available, image_url, is_unlimited, stock_quantity,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '22222222-2222-2222-2222-222222222221'::uuid, 'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid, 1,
    '후라이드 치킨', 'CHICKEN', 18000, '바삭바삭 후라이드', true, null, false, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO p_menus (
    menu_id, store_id, menu_num, menu_name, menu_category_code, price, description, is_available, image_url, is_unlimited, stock_quantity,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '22222222-2222-2222-2222-222222222222'::uuid, 'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid, 2,
    '양념 치킨', 'CHICKEN', 20000, '달콤매콤 양념치킨', true, null, false, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO p_menus (
    menu_id, store_id, menu_num, menu_name, menu_category_code, price, description, is_available, image_url, is_unlimited, stock_quantity,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '33333333-3333-3333-3333-333333333331'::uuid, 'dddddddd-dddd-dddd-dddd-dddddddddddd'::uuid, 1,
    '치즈버거', 'BURGER', 8000, '진짜 치즈버거', true, null, false, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO p_menus (
    menu_id, store_id, menu_num, menu_name, menu_category_code, price, description, is_available, image_url, is_unlimited, stock_quantity,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '33333333-3333-3333-3333-333333333332'::uuid, 'dddddddd-dddd-dddd-dddd-dddddddddddd'::uuid, 2,
    '불고기버거', 'BURGER', 10000, '한국식 불고기버거', true, null, false, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO p_menus (
    menu_id, store_id, menu_num, menu_name, menu_category_code, price, description, is_available, image_url, is_unlimited, stock_quantity,
    created_at, created_by, updated_at, updated_by
) VALUES (
    '33333333-3333-3333-3333-333333333333'::uuid, 'dddddddd-dddd-dddd-dddd-dddddddddddd'::uuid, 3,
    '감자튀김', 'SIDE', 4000, '바삭한 감자튀김', true, null, false, 0,
    now(), 'system', now(), 'system'
) ON CONFLICT (menu_id) DO NOTHING;