CREATE TABLE lead_distribution_rules (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    platform VARCHAR(50) NOT NULL,
    page_id VARCHAR(100) NOT NULL,
    page_name VARCHAR(255),
    campaign_id VARCHAR(100) NOT NULL,
    campaign_name VARCHAR(255),
    adset_id VARCHAR(100) NOT NULL,
    adset_name VARCHAR(255),
    ad_id VARCHAR(100) NOT NULL,
    ad_name VARCHAR(255),
    current_index INTEGER DEFAULT 0,
    current_count INTEGER DEFAULT 0,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_lead_distribution_rule UNIQUE (organization_id, platform, page_id, campaign_id, adset_id, ad_id)
);

CREATE INDEX idx_lead_distribution_rule_org_platform
    ON lead_distribution_rules (organization_id, platform);

CREATE TABLE lead_distribution_assignments (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES lead_distribution_rules(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    frequency INTEGER NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_lead_distribution_assignment UNIQUE (rule_id, user_id)
);

CREATE INDEX idx_lead_distribution_assignment_rule
    ON lead_distribution_assignments (rule_id);
