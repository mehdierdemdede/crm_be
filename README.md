# CRM Backend

This repository now only contains the backend services for the CRM project. The CRM frontend has been removed and will be managed in a separate repository.

## Local development quick start

1. **Prerequisites**
   - Java 21 (the Gradle wrapper configures the toolchain automatically).
   - PostgreSQL 14+ with a database/user that matches the credentials you configure below.
   - `./gradlew` is committed, so no global Gradle install is required.
2. **Configure application properties** – copy `src/main/resources/application.properties` or override the values with environment variables before running the app. At minimum you should set the datasource credentials, JWT secret, encryption key, mail credentials, and the frontend URLs.
3. **Initialize the schema** – start the app once to let Hibernate create/update the tables (default `spring.jpa.hibernate.ddl-auto=update`) or apply your own SQL migrations externally.
4. **Start the API** – `./gradlew bootRun` (dev) or `./gradlew bootJar && java -jar build/libs/*.jar` (packaged jar).
5. **Execute tests** – `./gradlew test`.

### Common environment overrides

All Spring Boot properties can be overridden through environment variables in production. The most relevant ones for frontend integration are:

| Property | Description |
| --- | --- |
| `APP_FRONTEND_BASE_URL` | Base URL of the deployed frontend; used for OAuth redirect targets and invitation links. |
| `APP_CORS_ALLOWED_ORIGINS` | Comma separated list of origins that are allowed to call this API. Set this to your frontend host(s). |
| `APP_OAUTH2_FRONTEND_SUCCESS_REDIRECT_URL` / `APP_OAUTH2_FRONTEND_ERROR_REDIRECT_URL` | Only override if the default `${app.frontend.base-url}` paths are not suitable. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | PostgreSQL connection info for the environment. |
| `APP_JWT_SECRET`, `APP_ENCRYPTION_KEY` | Secrets used for token signing and encrypting stored OAuth credentials. |
| `SPRING_MAIL_*` | SMTP provider credentials for invitation and notification emails. |

> 🔐 Do **not** commit real secrets to `application.properties`. Populate them from environment variables in each environment instead.

## Deploying backend and frontend together

The backend and frontend can be deployed independently while sharing the same domain via a reverse proxy:

1. **Build the backend jar**: `./gradlew bootJar` produces `build/libs/crm-*.jar`. Run it behind your web server (e.g., `java -jar crm-be.jar`).
2. **Deploy the frontend**: build the frontend bundle in its own repository and serve the static files (e.g., via Nginx, S3, or the same reverse proxy hosting the backend).
3. **Configure a proxy** so that `/api/**` routes are forwarded to the backend service while other routes serve the frontend bundle. Ensure HTTPS is terminated at the proxy for both apps.
4. **Update environment configuration**:
   - Set `APP_FRONTEND_BASE_URL` to the public frontend URL (for example, `https://app.example.com`).
   - Set `APP_CORS_ALLOWED_ORIGINS` to the same URL so browsers can call the API.
   - Update OAuth redirect URIs at Facebook/Google to point to the backend host (e.g., `https://api.example.com/api/integrations/oauth2/callback/facebook`).
   - If the frontend needs to call the backend from the same domain, configure it to use the proxied path (`/api`). Otherwise set its API base URL to the backend origin.
5. **Smoke test the integration** by logging in, completing OAuth flows, and running the manual Facebook sync from the frontend. Check the backend logs and `integration_logs` table for errors.

For more frontend-specific integration details see [`docs/frontend/facebook-lead-sync.md`](docs/frontend/facebook-lead-sync.md).

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

## Configuring frontend origins

Set `app.cors.allowed-origins` in `application.properties` (or via environment variables) to a comma-separated list of frontend URLs that should be able to call the backend. The default value is `http://localhost:3000` for local development.

## Bootstrap a global SUPER_ADMIN

After resetting PostgreSQL you can restore the global control user by executing the helper script manually:

```sql
\i docs/sql/seed_super_admin.sql
```

The script ensures the `pgcrypto` extension is available and hashes the password `password` with bcrypt on the server before inserting the row. The default credentials are:

- **Email:** `super.admin@global.local`
- **Password:** `password`

The user is created under the `Global Control` organization (`11111111-2222-3333-4444-555555555555`). You can change these constants in the script/migration if you need a different bootstrap setup.
