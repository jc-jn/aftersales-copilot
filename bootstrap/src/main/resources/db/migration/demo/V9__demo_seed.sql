-- All demo accounts use password: Demo@123456
INSERT INTO sys_user (id, username, email, password_hash, display_name, role, status, agent_online, last_assigned_at, last_login_at, version, created_at, updated_at, deleted_at) VALUES
    (1001, 'customer01', 'customer01@example.test', '$2b$10$42Rs7b8lU.XnLcOOxs.tBunwC8VqfI4w/qmHS5DsA2q5B1lqvCq6W', '演示用户', 'CUSTOMER', 'ACTIVE', 0, NULL, NULL, 0, '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL),
    (1002, 'customer02', 'customer02@example.test', '$2b$10$42Rs7b8lU.XnLcOOxs.tBunwC8VqfI4w/qmHS5DsA2q5B1lqvCq6W', '其他用户', 'CUSTOMER', 'ACTIVE', 0, NULL, NULL, 0, '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL),
    (1101, 'agent01', 'agent01@example.test', '$2b$10$42Rs7b8lU.XnLcOOxs.tBunwC8VqfI4w/qmHS5DsA2q5B1lqvCq6W', '演示客服', 'AGENT', 'ACTIVE', 1, NULL, NULL, 0, '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL),
    (1201, 'admin01', 'admin01@example.test', '$2b$10$42Rs7b8lU.XnLcOOxs.tBunwC8VqfI4w/qmHS5DsA2q5B1lqvCq6W', '演示管理员', 'ADMIN', 'ACTIVE', 0, NULL, NULL, 0, '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL);

INSERT INTO product (id, product_code, name, brand, category, description, status, created_at, updated_at, deleted_at) VALUES
    (2001, 'PHONE-X1', '星云 X1 手机', '星云', '手机', '演示旗舰手机', 'ACTIVE', '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL),
    (2002, 'BUDS-PRO', '星云降噪耳机 Pro', '星云', '耳机', '演示真无线降噪耳机', 'ACTIVE', '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL),
    (2003, 'KEYBOARD-K8', '极光 K8 机械键盘', '极光', '键盘', '演示三模机械键盘', 'ACTIVE', '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL);

INSERT INTO product_sku (id, product_id, sku_code, spec_json, sale_price_cent, warranty_months, serial_required, status, created_at, updated_at, deleted_at) VALUES
    (2101, 2001, 'PHONE-X1-BLK-256', JSON_OBJECT('color', '曜石黑', 'storage', '256GB'), 399900, 12, 1, 'ACTIVE', '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL),
    (2102, 2002, 'BUDS-PRO-WHT', JSON_OBJECT('color', '云杉白'), 89900, 12, 1, 'ACTIVE', '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL),
    (2103, 2003, 'KEYBOARD-K8-GRY', JSON_OBJECT('color', '深空灰', 'switch', '茶轴'), 49900, 24, 0, 'ACTIVE', '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000', NULL);

INSERT INTO warranty_rule (id, rule_code, name, scope_type, scope_id, refund_only_days, return_refund_days, exchange_days, repair_days, requires_unopened, effective_from, effective_to, status, rule_json, version, created_at, updated_at) VALUES
    (2201, 'GLOBAL-DEFAULT-2026', '全局默认售后规则', 'GLOBAL', NULL, 7, 7, 15, 365, 0, '2026-01-01 00:00:00.000', NULL, 'ACTIVE', JSON_OBJECT('description', '演示环境默认规则'), 0, '2026-09-01 00:00:00.000', '2026-09-01 00:00:00.000');

INSERT INTO customer_order (id, order_no, customer_id, status, total_amount_cent, paid_amount_cent, paid_at, shipped_at, delivered_at, receiver_snapshot, version, created_at, updated_at) VALUES
    (3001, 'ORD202609120001', 1001, 'DELIVERED', 399900, 399900, '2026-09-12 02:00:00.000', '2026-09-13 01:00:00.000', '2026-09-15 06:30:00.000', JSON_OBJECT('name', '张*', 'phone', '138****0001', 'address', '上海市浦东新区***'), 0, '2026-09-12 02:00:00.000', '2026-09-15 06:30:00.000'),
    (3002, 'ORD202609160001', 1001, 'SHIPPED', 89900, 89900, '2026-09-16 03:00:00.000', '2026-09-17 01:00:00.000', NULL, JSON_OBJECT('name', '张*', 'phone', '138****0001', 'address', '上海市浦东新区***'), 0, '2026-09-16 03:00:00.000', '2026-09-17 01:00:00.000'),
    (3003, 'ORD202608010001', 1002, 'DELIVERED', 49900, 49900, '2026-08-01 02:00:00.000', '2026-08-02 01:00:00.000', '2026-08-04 06:30:00.000', JSON_OBJECT('name', '李*', 'phone', '139****0002', 'address', '北京市海淀区***'), 0, '2026-08-01 02:00:00.000', '2026-08-04 06:30:00.000');

INSERT INTO order_item (id, order_id, sku_id, product_name_snapshot, sku_spec_snapshot, unit_price_cent, quantity, paid_amount_cent, refunded_amount_cent, serial_number, warranty_expire_at, version, created_at, updated_at) VALUES
    (3101, 3001, 2101, '星云 X1 手机', JSON_OBJECT('color', '曜石黑', 'storage', '256GB'), 399900, 1, 399900, 0, 'NX1DEMO000001', '2027-09-15 06:30:00.000', 0, '2026-09-12 02:00:00.000', '2026-09-15 06:30:00.000'),
    (3102, 3002, 2102, '星云降噪耳机 Pro', JSON_OBJECT('color', '云杉白'), 89900, 1, 89900, 0, 'NBPDEMO000001', '2027-09-20 00:00:00.000', 0, '2026-09-16 03:00:00.000', '2026-09-17 01:00:00.000'),
    (3103, 3003, 2103, '极光 K8 机械键盘', JSON_OBJECT('color', '深空灰', 'switch', '茶轴'), 49900, 1, 49900, 0, NULL, '2028-08-04 06:30:00.000', 0, '2026-08-01 02:00:00.000', '2026-08-04 06:30:00.000');

INSERT INTO payment_record (id, payment_no, order_id, channel, amount_cent, status, paid_at, created_at, updated_at) VALUES
    (3201, 'PAY202609120001', 3001, 'MOCK', 399900, 'PAID', '2026-09-12 02:00:00.000', '2026-09-12 02:00:00.000', '2026-09-12 02:00:00.000'),
    (3202, 'PAY202609160001', 3002, 'MOCK', 89900, 'PAID', '2026-09-16 03:00:00.000', '2026-09-16 03:00:00.000', '2026-09-16 03:00:00.000'),
    (3203, 'PAY202608010001', 3003, 'MOCK', 49900, 'PAID', '2026-08-01 02:00:00.000', '2026-08-01 02:00:00.000', '2026-08-01 02:00:00.000');

INSERT INTO logistics_shipment (id, shipment_no, order_id, type, carrier_code, tracking_no, status, shipped_at, delivered_at, created_at, updated_at) VALUES
    (3301, 'SHP202609130001', 3001, 'OUTBOUND', 'SF', 'SFDEMO202609130001', 'DELIVERED', '2026-09-13 01:00:00.000', '2026-09-15 06:30:00.000', '2026-09-13 01:00:00.000', '2026-09-15 06:30:00.000'),
    (3302, 'SHP202609170001', 3002, 'OUTBOUND', 'JD', 'JDDEMO202609170001', 'IN_TRANSIT', '2026-09-17 01:00:00.000', NULL, '2026-09-17 01:00:00.000', '2026-09-20 08:00:00.000'),
    (3303, 'SHP202608020001', 3003, 'OUTBOUND', 'YTO', 'YTODEMO202608020001', 'DELIVERED', '2026-08-02 01:00:00.000', '2026-08-04 06:30:00.000', '2026-08-02 01:00:00.000', '2026-08-04 06:30:00.000');

INSERT INTO logistics_event (id, shipment_id, event_time, status_code, description, location, created_at, updated_at) VALUES
    (3401, 3301, '2026-09-13 01:00:00.000', 'PICKED_UP', '快件已揽收', '深圳市', '2026-09-13 01:00:00.000', '2026-09-13 01:00:00.000'),
    (3402, 3301, '2026-09-14 03:00:00.000', 'IN_TRANSIT', '快件运输中', '杭州市', '2026-09-14 03:00:00.000', '2026-09-14 03:00:00.000'),
    (3403, 3301, '2026-09-15 06:30:00.000', 'DELIVERED', '快件已签收', '上海市', '2026-09-15 06:30:00.000', '2026-09-15 06:30:00.000'),
    (3404, 3302, '2026-09-17 01:00:00.000', 'PICKED_UP', '快件已揽收', '深圳市', '2026-09-17 01:00:00.000', '2026-09-17 01:00:00.000'),
    (3405, 3302, '2026-09-20 08:00:00.000', 'IN_TRANSIT', '快件运输中', '苏州市', '2026-09-20 08:00:00.000', '2026-09-20 08:00:00.000'),
    (3406, 3303, '2026-08-04 06:30:00.000', 'DELIVERED', '快件已签收', '北京市', '2026-08-04 06:30:00.000', '2026-08-04 06:30:00.000');
