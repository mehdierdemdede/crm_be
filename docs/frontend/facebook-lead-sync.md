# Facebook Lead Sync - Frontend Integration Notes

These notes summarize everything the frontend needs to connect a Facebook page, trigger
manual lead fetches, and surface the results to users.

## 1. OAuth connection flow

1. **Get the authorization URL**
   - Endpoint: `GET /api/integrations/oauth2/authorize/facebook`
   - Auth: JWT (only `SUPER_ADMIN` can call this today)
   - Response: raw Facebook authorization URL. Redirect the browser to this URL.

2. **Facebook redirects back to backend**
   - Our backend handles `GET /api/integrations/oauth2/callback/facebook`.
   - On success the user is redirected to `${app.oauth2.frontend-success-redirect-url}`.
   - On error we redirect to `${app.oauth2.frontend-error-redirect-url}?message=...`.
   - Frontend just needs to provide success / error landing pages matching these URLs.

3. **Persisted state**
   - After the callback completes the backend stores encrypted access tokens and the selected page ID.
   - The frontend can confirm connection status by calling `GET /api/integrations/facebook` (requires
     `ADMIN` or `SUPER_ADMIN`).

## 2. Manual lead fetch

Once a Facebook page is connected, the frontend can offer a "Fetch leads" CTA.

- Endpoint: `POST /api/integrations/fetch-leads/facebook`
- Auth: JWT with `ADMIN` or `SUPER_ADMIN` role.
- Body: **no payload** – organization is inferred from the authenticated user.
- Response JSON:

  ```json
  {
    "fetched": 12,
    "created": 7,
    "updated": 5
  }
  ```

  - `fetched`: total Facebook leads processed in this run.
  - `created`: number of new CRM leads inserted.
  - `updated`: number of existing leads that received Facebook updates.

- Error handling: standard HTTP errors with message bodies. Show a toast/snackbar with the message.
- The service automatically refreshes page tokens, keeps track of the last synced timestamp, and skips
  duplicates, so the frontend does not need additional parameters.

### Suggested UI copy

| State                | Suggested message                                  |
|----------------------|-----------------------------------------------------|
| Success              | "Facebook lead sync finished: 7 new, 5 updated."   |
| Already up to date   | "Facebook leads are already up to date."           |
| Access denied (403)  | "You don't have permission to sync Facebook leads."|
| No integration (404) | "Connect a Facebook page before syncing leads."    |

## 3. Displaying sync history (optional)

- Endpoint: `GET /api/integrations/logs?platform=FACEBOOK`
- Auth: JWT with `ADMIN` or `SUPER_ADMIN` role.
- Response: array of `IntegrationLog` objects (same schema already used elsewhere).
- Use the log timestamps plus `details` JSON to show past sync attempts.

## 4. Frontend checklist

- [ ] Provide buttons/views to start OAuth, handle success/error redirects.
- [ ] Call the manual fetch endpoint and surface `fetched/created/updated` counts.
- [ ] Optionally render history using the logs endpoint.
- [ ] Gracefully handle 401/403/404 responses with helpful copy.

With these endpoints wired, the frontend can both connect a Facebook page and manually fetch the latest leads
using the backend logic implemented in this repository.
