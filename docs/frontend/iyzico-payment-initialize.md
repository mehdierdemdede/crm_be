# Legacy Iyzico payment initialization ("/api/payments/initialize")

Use this reference to build the request body for legacy clients that still call
`POST /api/payments/initialize`. The endpoint tokenizes the card via the Iyzico
client and returns a single-use token; it does not charge the card directly.

## Required payload structure

- **planId** (UUID): Selected plan identifier.
- **billingPeriod** (`MONTH` | `YEAR`): Subscription period.
- **seatCount** (1-200): Number of seats to purchase.
- **account**
  - **firstName**, **lastName**: Up to 100 characters each; cannot be blank.
  - **email**: Valid email, up to 255 characters.
  - **password**: 8-255 characters.
  - **phone**: Optional, up to 40 characters.
- **organization**
  - **organizationName**: Up to 255 characters.
  - **country**: ISO alpha-2 or alpha-3 country code (2–3 letters, e.g., `TR`).
  - **taxNumber**: Up to 50 characters.
  - **companySize**: Optional, up to 50 characters.
- **card**
  - **cardHolderName**: Up to 255 characters.
  - **cardNumber**: 12–19 digits, no spaces.
  - **expireMonth**: Numeric month `1–12`.
  - **expireYear**: Four-digit year `2000–2100`.
  - **cvc**: 3–4 digits.

## Notes for the frontend

- Send the **country** as a 2- or 3-letter ISO code to satisfy validation and
  Iyzico expectations.
- If you still send the legacy `card` shape with string month/year values, the
  backend will coerce them, but prefer the primary `card` object above.
- The endpoint only tokenizes the card; subsequent subscription creation will
  use the returned token alongside the other fields in this payload.
