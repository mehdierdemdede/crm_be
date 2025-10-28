-- Flyway migration to ensure a global SUPER_ADMIN user exists.
-- This mirrors the manual seed script under docs/sql.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH upserted_org AS (
    INSERT INTO organizations (id, name, created_at, updated_at)
    VALUES (
        '11111111-2222-3333-4444-555555555555',
        'Global Control',
        NOW(),
        NOW()
    )
    ON CONFLICT (name) DO UPDATE
        SET updated_at = EXCLUDED.updated_at
    RETURNING id
)
INSERT INTO users (
    id,
    organization_id,
    email,
    password_hash,
    first_name,
    last_name,
    role,
    is_active,
    auto_assign_enabled,
    daily_capacity,
    created_at,
    updated_at
)
SELECT
    'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    upserted_org.id,
    'super.admin@global.local',
    crypt('password', gen_salt('bf', 10)),
    'Global',
    'Admin',
    'SUPER_ADMIN',
    TRUE,
    FALSE,
    NULL,
    NOW(),
    NOW()
FROM upserted_org
ON CONFLICT (organization_id, email) DO UPDATE
    SET password_hash = EXCLUDED.password_hash,
        first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        role = EXCLUDED.role,
        is_active = EXCLUDED.is_active,
        auto_assign_enabled = EXCLUDED.auto_assign_enabled,
        daily_capacity = EXCLUDED.daily_capacity,
        updated_at = NOW();

COMMIT;
