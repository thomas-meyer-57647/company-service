CREATE TABLE deletion_tombstone (
    deletion_id VARCHAR(36) NOT NULL,
    company_id VARCHAR(36) NOT NULL,
    state VARCHAR(16) NOT NULL,
    requested_at_utc TIMESTAMP(3) NOT NULL,
    requested_by_sub VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    completed_at_utc TIMESTAMP(3) NULL,
    failed_at_utc TIMESTAMP(3) NULL,
    failure_reason VARCHAR(500) NULL,
    CONSTRAINT pk_deletion_tombstone PRIMARY KEY (deletion_id),
    CONSTRAINT uk_deletion_tombstone_company UNIQUE (company_id),
    CONSTRAINT chk_deletion_tombstone_state CHECK (state IN ('IN_PROGRESS', 'FAILED', 'COMPLETED'))
) ENGINE=InnoDB;

CREATE TABLE deletion_ack (
    deletion_id VARCHAR(36) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    acked_at_utc TIMESTAMP(3) NOT NULL,
    acked_by_sub VARCHAR(100) NOT NULL,
    CONSTRAINT pk_deletion_ack PRIMARY KEY (deletion_id, service_name),
    CONSTRAINT fk_deletion_ack_tombstone FOREIGN KEY (deletion_id) REFERENCES deletion_tombstone (deletion_id)
) ENGINE=InnoDB;

CREATE INDEX idx_deletion_tombstone_state ON deletion_tombstone (state);
CREATE INDEX idx_deletion_tombstone_idempotency_key ON deletion_tombstone (idempotency_key);
