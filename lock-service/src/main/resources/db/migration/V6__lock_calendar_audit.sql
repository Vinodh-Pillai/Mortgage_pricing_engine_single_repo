ALTER TABLE IF EXISTS lock_service.lock ADD COLUMN IF NOT EXISTS expiration_business_days INT;
ALTER TABLE IF EXISTS lock_service.lock ADD COLUMN IF NOT EXISTS expiration_calculated_at TIMESTAMPTZ;
ALTER TABLE IF EXISTS lock_service.lock ADD COLUMN IF NOT EXISTS calendar_config_hash VARCHAR(64);
