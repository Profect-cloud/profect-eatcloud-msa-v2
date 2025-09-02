\c order_db;

INSERT INTO order_status_codes (is_active, sort_order, code, display_name, created_at, created_by, updated_at, updated_by)
VALUES 
    (true, 1, 'PENDING', '대기중', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
    (true, 2, 'PAID', '결제완료', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
    (true, 3, 'PREPARING', '준비중', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
    (true, 4, 'READY', '준비완료', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
    (true, 5, 'COMPLETED', '완료', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
    (true, 6, 'CANCELLED', '취소', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

INSERT INTO order_type_codes (is_active, sort_order, code, display_name, created_at, created_by, updated_at, updated_by) 
VALUES 
    (true, 1, 'DELIVERY', '배달', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
    (true, 2, 'PICKUP', '포장', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

