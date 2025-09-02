INSERT INTO p_customer (id, name, nickname, email, password, phone_number, points, created_by, updated_by) VALUES
('550e8400-e29b-41d4-a716-446655440001', '김고객', '고객님', 'customer1@test.com', '$2a$10$7wKsJ19uMYYute4FXt2w/O3Fks3KaAWBrtraQQY8QobHe/vL0JiTm', '010-1234-5678', 1000, 'system', 'system'),
('550e8400-e29b-41d4-a716-446655440002', '이고객', '이님', 'customer2@test.com', '$2a$10$7wKsJ19uMYYute4FXt2w/O3Fks3KaAWBrtraQQY8QobHe/vL0JiTm', '010-2345-6789', 500, 'system', 'system'),
('550e8400-e29b-41d4-a716-446655440003', '박고객', '박님', 'customer3@test.com', '$2a$10$7wKsJ19uMYYute4FXt2w/O3Fks3KaAWBrtraQQY8QobHe/vL0JiTm', '010-3456-7890', 2000, 'system', 'system');


INSERT INTO p_addresses (id, customer_id, zipcode, road_addr, detail_addr, is_selected, created_by, updated_by) VALUES
('660e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', '12345', '서울시 강남구 테헤란로 123', '456동 789호', true, 'system', 'system'),
('660e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440002', '23456', '서울시 서초구 서초대로 456', '789동 012호', true, 'system', 'system');


INSERT INTO point_reservations (reservation_id, customer_id, order_id, points, status, reserved_at, created_by, updated_by) VALUES
('770e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', '880e8400-e29b-41d4-a716-446655440001', 100, 'RESERVED', now(), 'system', 'system'),
('770e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440002', '880e8400-e29b-41d4-a716-446655440002', 200, 'PROCESSED', now(), 'system', 'system'),
('770e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440003', '880e8400-e29b-41d4-a716-446655440003', 150, 'CANCELLED', now(), 'system', 'system');
