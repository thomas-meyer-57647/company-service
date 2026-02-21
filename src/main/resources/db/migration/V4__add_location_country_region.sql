ALTER TABLE location
    ADD COLUMN country_code VARCHAR(2) NULL AFTER timezone,
    ADD COLUMN region_code VARCHAR(32) NULL AFTER country_code;

CREATE INDEX idx_location_company_country_code ON location (company_id, country_code);
CREATE INDEX idx_location_company_region_code ON location (company_id, region_code);
