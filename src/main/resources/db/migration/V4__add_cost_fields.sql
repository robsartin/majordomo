-- V4: Add cost fields for Ledger service

ALTER TABLE properties ADD COLUMN purchase_price NUMERIC(12, 2);

ALTER TABLE service_records ADD COLUMN cost NUMERIC(12, 2);
