-- Demo billing data for integration and manual testing

-- Plans
INSERT INTO plan (id, code, name, description)
VALUES
    ('5d3c7f8b-8a8c-4f2e-9f8f-9b0c1d2e3f01', 'BASIC', 'Basic Plan', 'Entry level plan for growing teams'),
    ('7a9b2c3d-4e5f-4a6b-8c7d-8e9f0a1b2c3d', 'PRO', 'Pro Plan', 'Advanced automation and reporting features')
ON CONFLICT (code) DO NOTHING;

-- Prices for BASIC plan
INSERT INTO price (id, plan_id, billing_period, base_amount_cents, per_seat_amount_cents, currency)
SELECT '8c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e50', p.id, 'MONTH', 9900, 1500, 'TRY'
FROM plan p
WHERE p.code = 'BASIC'
  AND NOT EXISTS (
        SELECT 1 FROM price pr WHERE pr.plan_id = p.id AND pr.billing_period = 'MONTH');

INSERT INTO price (id, plan_id, billing_period, base_amount_cents, per_seat_amount_cents, currency)
SELECT '0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d', p.id, 'YEAR', 99000, 1200, 'TRY'
FROM plan p
WHERE p.code = 'BASIC'
  AND NOT EXISTS (
        SELECT 1 FROM price pr WHERE pr.plan_id = p.id AND pr.billing_period = 'YEAR');

-- Prices for PRO plan
INSERT INTO price (id, plan_id, billing_period, base_amount_cents, per_seat_amount_cents, currency)
SELECT '1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e', p.id, 'MONTH', 19900, 2500, 'TRY'
FROM plan p
WHERE p.code = 'PRO'
  AND NOT EXISTS (
        SELECT 1 FROM price pr WHERE pr.plan_id = p.id AND pr.billing_period = 'MONTH');

INSERT INTO price (id, plan_id, billing_period, base_amount_cents, per_seat_amount_cents, currency)
SELECT '2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f', p.id, 'YEAR', 199000, 2200, 'TRY'
FROM plan p
WHERE p.code = 'PRO'
  AND NOT EXISTS (
        SELECT 1 FROM price pr WHERE pr.plan_id = p.id AND pr.billing_period = 'YEAR');

-- Demo customer
INSERT INTO customer (id, external_id, email, company_name, status)
VALUES (
        '3d4e5f6a-7b8c-4d9e-0f1a-2b3c4d5e6f70',
        'CUST-DEMO-001',
        'demo.billing@leadsyncpro.test',
        'LeadSyncPro Demo',
        'ACTIVE')
ON CONFLICT (external_id) DO NOTHING;

-- Default payment method for the demo customer
INSERT INTO payment_method (id, customer_id, token_ref, is_default)
SELECT '4e5f6a7b-8c9d-4e0f-1a2b-3c4d5e6f7081', c.id, 'pm_token_demo_001', TRUE
FROM customer c
WHERE c.external_id = 'CUST-DEMO-001'
  AND NOT EXISTS (
        SELECT 1 FROM payment_method pm WHERE pm.customer_id = c.id AND pm.token_ref = 'pm_token_demo_001');

-- Active subscription for the demo customer on the BASIC MONTH plan
INSERT INTO subscription (
        id,
        customer_id,
        plan_id,
        price_id,
        status,
        start_at,
        current_period_start,
        current_period_end,
        cancel_at_period_end,
        trial_end_at,
        external_subscription_id,
        payment_method_id)
SELECT
        '5f6a7b8c-9d0e-4f1a-2b3c-4d5e6f708192',
        c.id,
        plan.id,
        price.id,
        'ACTIVE',
        TIMESTAMP WITH TIME ZONE '2024-01-01T00:00:00Z',
        TIMESTAMP WITH TIME ZONE '2024-01-01T00:00:00Z',
        TIMESTAMP WITH TIME ZONE '2024-01-31T23:59:59Z',
        FALSE,
        NULL,
        'sub_demo_001',
        pm.id
FROM customer c
        JOIN plan ON plan.code = 'BASIC'
        JOIN price ON price.id = '8c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e50'
        JOIN payment_method pm ON pm.customer_id = c.id AND pm.token_ref = 'pm_token_demo_001'
WHERE c.external_id = 'CUST-DEMO-001'
  AND NOT EXISTS (
        SELECT 1 FROM subscription s WHERE s.external_subscription_id = 'sub_demo_001');

-- Seat allocation for the demo subscription
INSERT INTO seat_allocation (id, subscription_id, seat_count, effective_from)
SELECT '6a7b8c9d-0e1f-4a2b-3c4d-5e6f708192a3', s.id, 10, TIMESTAMP WITH TIME ZONE '2024-01-01T00:00:00Z'
FROM subscription s
WHERE s.external_subscription_id = 'sub_demo_001'
  AND NOT EXISTS (
        SELECT 1 FROM seat_allocation sa WHERE sa.subscription_id = s.id);

-- Example invoice for the demo subscription
INSERT INTO invoice (
        id,
        subscription_id,
        period_start,
        period_end,
        subtotal_cents,
        tax_cents,
        total_cents,
        currency,
        status,
        external_invoice_id)
SELECT
        '7b8c9d0e-1f2a-4b3c-5d6e-7f8091a2b3c4',
        s.id,
        TIMESTAMP WITH TIME ZONE '2024-01-01T00:00:00Z',
        TIMESTAMP WITH TIME ZONE '2024-01-31T23:59:59Z',
        99000,
        18810,
        117810,
        'TRY',
        'PAID',
        'inv_demo_001'
FROM subscription s
WHERE s.external_subscription_id = 'sub_demo_001'
  AND NOT EXISTS (
        SELECT 1 FROM invoice i WHERE i.external_invoice_id = 'inv_demo_001');
