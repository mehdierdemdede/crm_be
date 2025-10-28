# CRM Backend

This repository now only contains the backend services for the CRM project. The CRM frontend has been removed and will be managed in a separate repository.

## Facebook Lead Fetch API

> Looking for the full frontend playbook? See [`docs/frontend/facebook-lead-sync.md`](docs/frontend/facebook-lead-sync.md).

The backend exposes a manual sync endpoint that the frontend can call once a Facebook integration is connected. The endpoint validates the current organization and uses the stored OAuth tokens to pull the newest leads.

```
POST /api/integrations/fetch-leads/facebook
Authorization: Bearer <JWT>
Content-Type: application/json
```

**Response**

```json
{
  "fetched": 12,
  "created": 7,
  "updated": 5
}
```

- `fetched`: total number of leads that were processed in this run.
- `created`: how many new CRM lead records were inserted.
- `updated`: how many existing CRM lead records were refreshed with Facebook data.

> ℹ️ Only `ADMIN` and `SUPER_ADMIN` roles can trigger this sync. The request uses the organization ID resolved from the authenticated user, so no additional parameters are required from the frontend.

Behind the scenes the backend automatically refreshes the page access token when needed, persists the latest Facebook lead timestamp per organization, and skips older leads that were already synchronized. Each run is also logged to the `integration_logs` table so the history can be displayed from the reporting screens.

## Bootstrap a global SUPER_ADMIN

After resetting PostgreSQL you can restore the global control user by either letting Flyway run the `V3__seed_global_super_admin.sql` migration or by executing the helper script manually:

```sql
\i docs/sql/seed_super_admin.sql
```

Both approaches ensure the `pgcrypto` extension is available and hash the password `password` with bcrypt on the server before inserting the row. The default credentials are:

- **Email:** `super.admin@global.local`
- **Password:** `password`

The user is created under the `Global Control` organization (`11111111-2222-3333-4444-555555555555`). You can change these constants in the script/migration if you need a different bootstrap setup.
