# CRM Tool – Backend Enhancement Requirements

## 1. Executive Summary
- Consolidates the gaps identified in the initial Turkish analysis document and the new enhancement brief.
- Prioritises improvements that directly impact user experience, operational efficiency, data integrity, and security.
- Details backend capabilities, data structures, and integration points required to support the refined scope.

## 2. Lead Management Enhancements
### 2.1 Data Model & Tracking
- Generate a unique lead identifier along with UTC acquisition timestamps.
- Persist audit timestamps (`created_at`, `updated_at`) and consent flags that fulfil marketing compliance rules.
- Capture source metadata (campaign, ad set, creative, form ID) for attribution reporting.
- Store full lead status history and user assignment changes for auditability.

### 2.2 Lead Lifecycle Automation
- SLA automation engine with configurable reminders per status and escalation routing.
- Auto-reassignment workflows when a lead is marked `Yanlış Numara` or `Block`; these leads move to an admin queue for redistribution.
- Cooling-off logic preventing leads from returning to the same agent for a configurable period after specific outcomes.

### 2.3 Communication Logging
- Unified activity timeline recording channel, timestamp, notes, attachments, and provider references.
- Template catalogue for WhatsApp, email, and SMS with personalisation variables.
- Dialer/WhatsApp bridge that masks phone numbers, records call outcomes and durations, and integrates with telephony providers.

### 2.4 Privacy & Security
- Field-level masking for phone numbers with role-based reveal (admin only).
- Secure proxy call/WhatsApp initiation keeping PII hidden from agents.
- Consent verification logs and unsubscribe tracking synchronised with marketing platforms.
- Encryption at rest for PII, plus retention policies and automated purging jobs.

### 2.5 UX-Oriented Backend Support
- Responsive list support (sticky headers, column visibility, inline filters) through flexible query APIs and cursor-based pagination.
- Bulk operations for status updates and assignments that enforce role permissions.
- Contextual status update metadata: provide SLA timers, previous actions, and suggested next steps via API responses.
- Empty state hints and loading skeleton data to support frontend clarity.

### 2.6 Secure Communication UX (Already Implemented on Frontend)
- Mask contact columns while signalling call/WhatsApp availability.
- Open call/WhatsApp actions inside the application using proxy-based modals.
- WhatsApp composer works entirely in-product, validates inputs, and reports delivery feedback without exposing raw numbers.
- Call modal returns secure session metadata (`sessionId`, status, expiry) confirming bridge activation.
- Success/error states produce toast notifications and contextual modal messages; backend must expose clear status codes and messages.

## 3. User & Role Management Enhancements
### 3.1 Role-Based Access Control (RBAC)
- Support roles: Admin, Sales Manager, Sales Agent, Compliance Auditor.
- Define granular permissions for viewing/editing leads, exporting data, managing templates, and revealing phone numbers.

### 3.2 Team & Territory Structure
- Group agents by teams/regions for routing, reporting, and balanced distribution.
- Configure working hours and capacity per user to influence the distribution engine.

### 3.3 Audit & Compliance
- Immutable audit logs tracking authentication, assignments, status updates, communications, and data exports.
- Enforce session timeout, MFA, and IP allow-listing for sensitive roles.

## 4. Distribution Engine Improvements
- Cascading routing rules (campaign → ad set → keyword → geography).
- Weighted round robin with real-time capacity checks and out-of-office overrides.
- A/B testing to split traffic between scripts or teams, recording conversion outcomes.

## 5. Reporting & Analytics
### 5.1 Dashboards
- Company, campaign, and agent dashboards with drill-down to individual lead timelines.
- SLA compliance widgets highlighting overdue follow-ups.

### 5.2 Metrics & KPIs
- Track lead response time, contact rate, conversion velocity, revenue per lead, and churn reasons.
- Provide funnel visualisations from lead → contacted → qualified → proposal → sale.

### 5.3 Data Export & BI Integration
- Scheduled CSV/Excel exports gated by role-based access.
- BI connector/API for aggregated metrics synchronisation to external analytics tools.

## 6. Backend Services & Infrastructure
### 6.1 Services & APIs
- Lead ingestion microservice handling Facebook Graph API, Google Ads API, and manual CSV uploads.
- Distribution service executing assignment rules, capacity checks, and SLA timers (cron/queue worker).
- Communication service integrating telephony/WhatsApp/email providers through webhooks.
- Secure contact orchestrator issuing proxy call sessions and first-party WhatsApp sends that align with the frontend modals.
- Reporting service aggregating metrics, caching dashboards, and exposing REST/GraphQL endpoints.

### 6.2 Data Storage
- PostgreSQL tables for leads, statuses, assignments, activities, campaigns, users, roles, SLA rules, communication templates, and audit logs.
- Message queue (e.g., RabbitMQ/Kafka) for asynchronous ingestion and reminders.
- Object storage (S3-compatible) for attachments and operational imagery.

### 6.3 Integrations & Webhooks
- OAuth token refresh workers for advertising platforms.
- Webhooks for inbound communications (WhatsApp replies, call events) that append to the activity timeline.
- CRM-to-ERP integration hooks triggered when a sale is completed.

### 6.4 Performance & Reliability
- Batch import endpoints with idempotency keys to avoid duplicates.
- Cursor-based pagination for large datasets, plus caching for common filters.
- Automated retries with exponential backoff and observability alerts for third-party API failures.

### 6.5 Security & Compliance
- JWT authentication with refresh tokens, scoped roles, and device binding.
- AES-256 encryption for PII fields, TLS everywhere, OWASP-hardened endpoints.
- GDPR/KVKK support: consent storage, right-to-erasure flows, and data retention schedulers.

### 6.6 Secure Communication Services (New UI Requirements)
- `POST /leads/{id}/call` → returns `{ callId, status, expiresAt?, dialUrl? }` without exposing phone numbers.
- `POST /leads/{id}/whatsapp` → returns `{ messageId, status, deliveredAt? }` after pushing WhatsApp Business API messages.
- Persist every request/response pair with failure reasons and provider references for audit.
- Throttling, retry logic, and timeout guards to surface actionable error messages to the UI.
- Webhook/polling endpoints updating delivery or connection status to feed toast and inline success states.

## 7. UX Deliverables (Backend Support)
- Provide APIs that enable user journey maps for admin and sales personas.
- Support low-fidelity wireframes for lead list/detail views, communication modals, and reporting dashboards.
- Deliver accessibility metadata (labels, roles) to satisfy WCAG 2.1 AA requirements.

## 8. Implementation Roadmap
1. **Foundation** – RBAC, data model upgrades, ingestion pipeline.
2. **Automation** – distribution engine, SLA workflows, communication logging.
3. **Experience** – enhanced APIs supporting UI components and secure telephony/WhatsApp flows.
4. **Analytics** – dashboards, exports, BI connectors.
5. **Compliance** – audit logging, retention, encryption, consent management.

## 9. Risks & Mitigations
- **API Limits** – cache tokens, schedule pulls respecting rate limits, prefer webhooks.
- **Data Quality** – dedupe logic, import validation, manual review queues.
- **Adoption** – phased rollout, training, in-app guidance, feedback loops.

## 10. Next Steps
- Validate refined scope with stakeholders, decide MVP versus later phases.
- Align backend service architecture and delivery timelines.
- Begin UI/UX prototyping before sprint planning.

## 11. Secure Communication Backend Checklist
- Finalise telephony/WhatsApp provider selection ensuring proxy calling and templated messaging.
- Map UI events to audit schema (`PHONE`, `WHATSAPP`) to persist new actions alongside existing activity logs.
- Define failure taxonomy (rate limit, invalid template, unreachable contact) for modal error surfacing.
- Extend notification workers to reconcile delivery receipts and update `callSession.status` / `whatsAppResult.status` asynchronously.
- Implement end-to-end integration tests covering masked number flows for KVKK/GDPR audits.

## Appendix – Alignment with the Original Analysis Document (Turkish)
- Lead management goals (centralised intake, agent distribution, status tracking) remain intact but now include consent logging and audit history.
- User roles expand from Admin/Sales to Admin, Sales Manager, Sales Agent, Compliance Auditor to support granular permissions.
- Distribution retains round-robin plus percentage-based rules, extended with capacity, territory, and A/B testing logic.
- Reminder schedules (`İlgili`, `İlgisiz`, `Cevapsız`, `Yanlış Numara`, `Block`) map to the configurable SLA engine and auto-reassignment workflows.
- Sales follow-up fields (operation type, pricing, post-operation check-ins) feed into the activity and reporting schema.
- Reporting enhancements cover user, campaign, and dashboard analytics while adding exports and BI integrations.
