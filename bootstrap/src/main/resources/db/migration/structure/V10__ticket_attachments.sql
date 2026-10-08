CREATE TABLE ticket_attachment (
    id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NOT NULL,
    uploaded_by BIGINT UNSIGNED NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_attachment_ticket (ticket_id, created_at),
    CONSTRAINT fk_attachment_ticket FOREIGN KEY (ticket_id) REFERENCES service_ticket(id),
    CONSTRAINT fk_attachment_user FOREIGN KEY (uploaded_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
