CREATE TABLE bootstrap_idempotency (
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    company_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    CONSTRAINT pk_bootstrap_idempotency PRIMARY KEY (idempotency_key)
) ENGINE=InnoDB;

CREATE INDEX idx_bootstrap_idempotency_company_id ON bootstrap_idempotency (company_id);
