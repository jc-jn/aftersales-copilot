CREATE TABLE customer_order (
    id BIGINT UNSIGNED NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount_cent BIGINT NOT NULL,
    paid_amount_cent BIGINT NOT NULL,
    paid_at DATETIME(3) NULL,
    shipped_at DATETIME(3) NULL,
    delivered_at DATETIME(3) NULL,
    receiver_snapshot JSON NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_order_no UNIQUE (order_no),
    INDEX idx_customer_order_customer_created (customer_id, created_at),
    INDEX idx_customer_order_customer_status (customer_id, status),
    CONSTRAINT fk_customer_order_user FOREIGN KEY (customer_id) REFERENCES sys_user (id),
    CONSTRAINT chk_customer_order_status CHECK (status IN ('PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_customer_order_amount CHECK (total_amount_cent >= 0 AND paid_amount_cent >= 0 AND paid_amount_cent <= total_amount_cent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_item (
    id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    product_name_snapshot VARCHAR(128) NOT NULL,
    sku_spec_snapshot JSON NOT NULL,
    unit_price_cent BIGINT NOT NULL,
    quantity INT NOT NULL,
    paid_amount_cent BIGINT NOT NULL,
    refunded_amount_cent BIGINT NOT NULL DEFAULT 0,
    serial_number VARCHAR(80) NULL,
    warranty_expire_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_item_order (order_id),
    INDEX idx_order_item_sku (sku_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT fk_order_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
    CONSTRAINT chk_order_item_amount CHECK (unit_price_cent >= 0 AND paid_amount_cent >= 0 AND refunded_amount_cent >= 0 AND refunded_amount_cent <= paid_amount_cent),
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_record (
    id BIGINT UNSIGNED NOT NULL,
    payment_no VARCHAR(32) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(20) NOT NULL,
    amount_cent BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    paid_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_record_no UNIQUE (payment_no),
    INDEX idx_payment_record_order (order_id),
    CONSTRAINT fk_payment_record_order FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT chk_payment_channel CHECK (channel = 'MOCK'),
    CONSTRAINT chk_payment_status CHECK (status IN ('PAID', 'REFUNDED', 'PARTIAL_REFUNDED')),
    CONSTRAINT chk_payment_amount CHECK (amount_cent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE logistics_shipment (
    id BIGINT UNSIGNED NOT NULL,
    shipment_no VARCHAR(32) NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(24) NOT NULL,
    carrier_code VARCHAR(32) NOT NULL,
    tracking_no VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    shipped_at DATETIME(3) NULL,
    delivered_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_logistics_shipment_no UNIQUE (shipment_no),
    CONSTRAINT uk_logistics_tracking UNIQUE (carrier_code, tracking_no),
    INDEX idx_logistics_shipment_order (order_id, type),
    CONSTRAINT fk_logistics_shipment_order FOREIGN KEY (order_id) REFERENCES customer_order (id),
    CONSTRAINT chk_logistics_shipment_type CHECK (type IN ('OUTBOUND', 'RETURN', 'REPLACEMENT', 'REPAIR_RETURN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE logistics_event (
    id BIGINT UNSIGNED NOT NULL,
    shipment_id BIGINT UNSIGNED NOT NULL,
    event_time DATETIME(3) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    description VARCHAR(255) NOT NULL,
    location VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_logistics_event_shipment_time (shipment_id, event_time),
    CONSTRAINT fk_logistics_event_shipment FOREIGN KEY (shipment_id) REFERENCES logistics_shipment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
