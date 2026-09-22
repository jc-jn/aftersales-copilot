CREATE TABLE ticket_assignment_log (
    id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NOT NULL,
    from_agent_id BIGINT UNSIGNED NULL,
    to_agent_id BIGINT UNSIGNED NULL,
    assign_type VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NULL,
    operator_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_assignment_ticket_time(ticket_id, created_at),
    CONSTRAINT fk_assignment_ticket FOREIGN KEY (ticket_id) REFERENCES service_ticket(id),
    CONSTRAINT fk_assignment_from_agent FOREIGN KEY (from_agent_id) REFERENCES sys_user(id),
    CONSTRAINT fk_assignment_to_agent FOREIGN KEY (to_agent_id) REFERENCES sys_user(id),
    CONSTRAINT fk_assignment_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id),
    CONSTRAINT chk_assignment_type CHECK (assign_type IN ('AUTO','MANUAL','CLAIM','TRANSFER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
