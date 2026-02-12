CREATE TABLE company (
    company_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NULL,
    timezone VARCHAR(64) NULL,
    locale VARCHAR(32) NULL,
    logo_file_ref VARCHAR(255) NULL,
    main_location_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    created_by VARCHAR(100) NULL,
    modified_at TIMESTAMP(3) NULL,
    modified_by VARCHAR(100) NULL,
    trashed_at TIMESTAMP(3) NULL,
    trashed_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_company PRIMARY KEY (company_id)
) ENGINE=InnoDB;

CREATE TABLE location (
    location_id VARCHAR(36) NOT NULL,
    company_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    location_code VARCHAR(64) NULL,
    timezone VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    closed_at TIMESTAMP(3) NULL,
    closed_by VARCHAR(100) NULL,
    closed_reason VARCHAR(500) NULL,
    trashed_at TIMESTAMP(3) NULL,
    trashed_by VARCHAR(100) NULL,
    trashed_cause VARCHAR(16) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    created_by VARCHAR(100) NULL,
    modified_at TIMESTAMP(3) NULL,
    modified_by VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_location PRIMARY KEY (location_id),
    CONSTRAINT fk_location_company FOREIGN KEY (company_id) REFERENCES company (company_id),
    CONSTRAINT chk_location_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_location_trashed_cause CHECK (trashed_cause IN ('MANUAL', 'CASCADE') OR trashed_cause IS NULL),
    CONSTRAINT uk_location_company_code UNIQUE (company_id, location_code)
) ENGINE=InnoDB;

CREATE INDEX idx_location_company_id ON location (company_id);
CREATE INDEX idx_location_company_status_trashed_at ON location (company_id, status, trashed_at);
