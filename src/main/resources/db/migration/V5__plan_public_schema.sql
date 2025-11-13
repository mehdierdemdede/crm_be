-- Extend plan and price tables to support public catalog metadata
ALTER TABLE plan
    ADD COLUMN IF NOT EXISTS features JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE price
    ADD COLUMN IF NOT EXISTS seat_limit INTEGER,
    ADD COLUMN IF NOT EXISTS trial_days INTEGER;

-- Populate demo plan metadata for BASIC and PRO plans
UPDATE plan
SET features = '["Pipeline otomasyonları", "Temel raporlar", "Entegrasyon API erişimi"]'::jsonb,
    metadata = '{
      "basePrice": 0,
      "perSeatPrice": 15,
      "basePrice_month": 99,
      "perSeatPrice_month": 15,
      "basePrice_year": 990,
      "perSeatPrice_year": 12
    }'::jsonb
WHERE code = 'BASIC';

UPDATE plan
SET features = '["Gelişmiş otomasyon", "Özel raporlama", "Öncelikli destek"]'::jsonb,
    metadata = '{
      "basePrice": 0,
      "perSeatPrice": 25,
      "basePrice_month": 199,
      "perSeatPrice_month": 25,
      "basePrice_year": 1990,
      "perSeatPrice_year": 22
    }'::jsonb
WHERE code = 'PRO';

UPDATE price SET trial_days = 14 WHERE billing_period = 'MONTH' AND plan_id IN (SELECT id FROM plan WHERE code IN ('BASIC', 'PRO'));
UPDATE price SET trial_days = 30 WHERE billing_period = 'YEAR' AND plan_id IN (SELECT id FROM plan WHERE code IN ('BASIC', 'PRO'));
