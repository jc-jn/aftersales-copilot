CREATE TABLE service_ticket (
    id BIGINT UNSIGNED NOT NULL,
    ticket_no VARCHAR(32) NOT NULL,
    customer_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NOT NULL,
    requested_type VARCHAR(24) NOT NULL,
    confirmed_type VARCHAR(24) NULL,
    status VARCHAR(24) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    client_request_id VARCHAR(64) NULL,
    assigned_agent_id BIGINT UNSIGNED NULL,
    first_response_at DATETIME(3) NULL,
    resolved_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    resolution_code VARCHAR(32) NULL,
    resolution_note TEXT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ticket_no UNIQUE (ticket_no),
    CONSTRAINT uk_ticket_customer_request UNIQUE (customer_id, client_request_id),
    INDEX idx_ticket_customer (customer_id, created_at),
    INDEX idx_ticket_agent_status (assigned_agent_id, status, updated_at),
    INDEX idx_ticket_status_priority (status, priority, created_at),
    CONSTRAINT fk_ticket_customer FOREIGN KEY (customer_id) REFERENCES sys_user(id),
    CONSTRAINT fk_ticket_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
    CONSTRAINT fk_ticket_order_item FOREIGN KEY (order_item_id) REFERENCES order_item(id),
    CONSTRAINT fk_ticket_agent FOREIGN KEY (assigned_agent_id) REFERENCES sys_user(id),
    CONSTRAINT chk_ticket_status CHECK (status IN ('SUBMITTED','PENDING_ASSIGNMENT','PENDING_AGENT','PENDING_CUSTOMER','WAITING_RETURN','RETURN_IN_TRANSIT','RETURN_RECEIVED','PROCESSING','RESOLVED','REJECTED','CANCELLED','CLOSED')),
    CONSTRAINT chk_ticket_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE active_ticket_guard (
    order_item_id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (order_item_id),
    CONSTRAINT uk_active_ticket_guard_ticket UNIQUE (ticket_id),
    CONSTRAINT fk_active_guard_item FOREIGN KEY (order_item_id) REFERENCES order_item(id),
    CONSTRAINT fk_active_guard_ticket FOREIGN KEY (ticket_id) REFERENCES service_ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_message (
    id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    sender_id BIGINT UNSIGNED NULL,
    visibility VARCHAR(20) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id), INDEX idx_ticket_message_ticket_time(ticket_id, created_at),
    CONSTRAINT fk_ticket_message_ticket FOREIGN KEY (ticket_id) REFERENCES service_ticket(id),
    CONSTRAINT chk_ticket_message_visibility CHECK (visibility IN ('PUBLIC','INTERNAL')),
    CONSTRAINT chk_ticket_message_type CHECK (message_type IN ('TEXT','REQUEST_INFO','PROPOSAL','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_timeline (
    id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    summary VARCHAR(255) NOT NULL,
    detail_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id), INDEX idx_ticket_timeline_ticket_time(ticket_id, created_at),
    CONSTRAINT fk_ticket_timeline_ticket FOREIGN KEY (ticket_id) REFERENCES service_ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
