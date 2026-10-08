CREATE TABLE ai_call_log (
    call_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    task_id BIGINT UNSIGNED NULL,
    ticket_id BIGINT UNSIGNED NULL,
    operation VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NULL,
    model VARCHAR(128) NULL,
    prompt_version VARCHAR(64) NULL,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    estimated_cost_micros BIGINT NULL,
    cost_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    price_source VARCHAR(500) NULL,
    latency_ms BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    PRIMARY KEY (call_id),
    KEY idx_ai_call_created (created_at),
    KEY idx_ai_call_task (task_id),
    KEY idx_ai_call_status (status,created_at),
    CONSTRAINT chk_ai_call_status CHECK (status IN ('PENDING','SUCCEEDED','FAILED','INTERRUPTED','HISTORICAL')),
    CONSTRAINT chk_ai_call_tokens CHECK ((input_tokens IS NULL OR input_tokens >= 0) AND (output_tokens IS NULL OR output_tokens >= 0)),
    CONSTRAINT chk_ai_call_cost CHECK (estimated_cost_micros IS NULL OR estimated_cost_micros >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Old callbacks discarded provider usage. Preserve the analyses, but do not imply free calls.
INSERT INTO ai_call_log(call_id,trace_id,task_id,ticket_id,operation,provider,model,prompt_version,cost_status,status,created_at,finished_at)
SELECT CONCAT('historical-',id), 'historical', task_id,ticket_id,'TICKET_ANALYSIS',provider,model,prompt_version,'HISTORICAL_UNKNOWN','HISTORICAL',created_at,created_at
FROM ai_analysis;

UPDATE ai_analysis SET input_tokens=NULL,output_tokens=NULL,estimated_cost_micros=NULL,latency_ms=NULL;
