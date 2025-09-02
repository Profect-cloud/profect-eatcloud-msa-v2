INSERT INTO payment_method_codes (code, display_name, sort_order, is_active, created_at, created_by, updated_at, updated_by)
VALUES ('CARD', '카드', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('VIRTUAL_ACCOUNT', '가상계좌', 2, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('TRANSFER', '계좌이체', 3, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('PHONE', '휴대폰', 4, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('GIFT_CERTIFICATE', '상품권', 5, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('POINT', '포인트', 6, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

INSERT INTO payment_status_codes (code, display_name, sort_order, is_active, created_at, created_by, updated_at, updated_by)
VALUES ('PENDING', '대기중', 1, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('PROCESSING', '처리중', 2, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('COMPLETED', '완료', 3, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('APPROVED', '승인완료', 4, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('FAILED', '실패', 5, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('CANCELLED', '취소', 6, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
       ('REFUNDED', '환불', 7, true, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');
