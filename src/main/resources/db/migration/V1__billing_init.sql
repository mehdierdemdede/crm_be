-- Billing schema initialization

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE customer_status_enum AS ENUM ('ACTIVE', 'INACTIVE');
CREATE TYPE billing_period_enum AS ENUM ('MONTH', 'YEAR');
CREATE TYPE subscription_status_enum AS ENUM ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELED');
CREATE TYPE invoice_status_enum AS ENUM ('DRAFT', 'OPEN', 'PAID', 'VOID');
CREATE TYPE webhook_event_status_enum AS ENUM ('PENDING', 'PROCESSED', 'FAILED');

CREATE TABLE customer (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_id VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    status customer_status_enum NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE plan (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_plan_code_unique ON plan (code);

CREATE TABLE price (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id UUID NOT NULL,
    billing_period billing_period_enum NOT NULL,
    base_amount_cents BIGINT NOT NULL,
    per_seat_amount_cents BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_price_plan FOREIGN KEY (plan_id) REFERENCES plan (id)
);

CREATE INDEX idx_price_plan_id ON price (plan_id);

CREATE TABLE payment_method (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL,
    brand VARCHAR(100) NOT NULL,
    last4 VARCHAR(4) NOT NULL,
    token_ref VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_payment_method_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE INDEX idx_payment_method_customer_id ON payment_method (customer_id);

CREATE TABLE subscription (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    price_id UUID NOT NULL,
    status subscription_status_enum NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    cancel_at_period_end BOOLEAN DEFAULT FALSE,
    trial_end_at TIMESTAMP WITH TIME ZONE,
    external_subscription_id VARCHAR(255),
    payment_method_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_subscription_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_subscription_plan FOREIGN KEY (plan_id) REFERENCES plan (id),
    CONSTRAINT fk_subscription_price FOREIGN KEY (price_id) REFERENCES price (id),
    CONSTRAINT fk_subscription_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_method (id)
);

CREATE INDEX idx_subscription_customer_id ON subscription (customer_id);
CREATE INDEX idx_subscription_plan_id ON subscription (plan_id);
CREATE INDEX idx_subscription_price_id ON subscription (price_id);
CREATE INDEX idx_subscription_payment_method_id ON subscription (payment_method_id);

CREATE TABLE seat_allocation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subscription_id UUID NOT NULL,
    seat_count INTEGER NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_seat_allocation_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id)
);

CREATE INDEX idx_seat_allocation_subscription_id ON seat_allocation (subscription_id);

CREATE TABLE invoice (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subscription_id UUID NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    subtotal_cents BIGINT NOT NULL,
    tax_cents BIGINT NOT NULL,
    total_cents BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status invoice_status_enum NOT NULL,
    external_invoice_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_invoice_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id)
);

CREATE INDEX idx_invoice_subscription_id ON invoice (subscription_id);

CREATE TABLE webhook_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider VARCHAR(100) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    signature VARCHAR(512),
    processed_at TIMESTAMP WITH TIME ZONE,
    status webhook_event_status_enum NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_webhook_event_provider_event_id ON webhook_event (provider_event_id);

