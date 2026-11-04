# Authentication

There are two completely separate login worlds. Never let one grant access to the other.

| Who | How | Status |
|---|---|---|
| Admin | Google OAuth (Gmail), allow-list | **Built** |
| Customer | Smart-ID, direct connection to SK | Planned |
| Customer (demo only) | Test account | Planned |

## Admins: Google OAuth (Gmail)

- Spring Security `oauth2Login` with Google (OpenID Connect). Code: `backend/src/main/java/com/webshop/auth/`.
- Only emails in the **allow-list** `ADMIN_EMAILS` (comma-separated, in `backend/.env`) can log in.
  Google must report `email_verified=true`. Any other Google account is rejected, and the browser is
  sent back to `/admin?loginError`.
- Flow: the frontend `/admin` page calls `GET /api/me`. On 401 it shows a "Log in with Google"
  link to `{backend}/oauth2/authorization/google`. After login Google redirects to the backend,
  and the backend redirects to `{FRONTEND_URL}/admin`.
- Protected (needs `ROLE_ADMIN`): `POST/PUT/DELETE /api/products/**` and everything under `/api/admin/**`.
  The frontend `/admin` guard only exists for UX; the backend check is the real protection.
- Secrets: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` in `backend/.env` (never committed).
- Google Cloud Console: create an OAuth client of type "Web application" with redirect URI
  `http://localhost:3000/login/oauth2/code/google` (plus the production URL later).

## Customers: Smart-ID (Estonia), connected **directly** to SK

We integrate directly with the **SK ID Solutions Smart-ID Relying Party (RP) API v3**, with no broker
in between. Always check SK's current documentation when implementing; don't rely on memory for
endpoints, field names or certificate handling.

### Two environments: demo and production

Which one is used is selected by `SMARTID_ENV=demo|production` in `backend/.env`. The two must never mix.

| | **Demo** (development, testing, demos) | **Production** (real customers) |
|---|---|---|
| SK environment | SK **demo** RP API, with SK's public demo relying party UUID/name | SK **production** RP API, with our own RP UUID + name from the SK contract |
| Who can log in | SK demo Smart-ID accounts (demo app / SK test identities) **and** our local **test account** | Real Smart-ID users only |
| Login methods | QR code, choose-number, test account | **Only** QR code or choose-number |
| Test account | Enabled | **Disabled. The app must refuse to start if it's enabled with `SMARTID_ENV=production`.** |

### Login methods (both environments)

1. **QR code** (device link, cross-device). The site shows a QR code that refreshes regularly, as SK
   requires. The user scans it with the Smart-ID app and confirms. The backend polls the session
   status from SK.
2. **Choose number** (notification-based with verification-code choice). The user enters their
   Estonian personal code (isikukood). The Smart-ID app shows several numbers and the user picks the
   one our site displays. The backend polls the session status from SK.

Nothing else is allowed in production: no password login, no test account, no "skip".

### Rules for the Smart-ID integration

- Each login creates a fresh random challenge on the backend. Verify SK's response signature and the
  user's certificate (chain to SK's trusted CAs, validity, certificate level `QUALIFIED`), as SK's docs
  require. Never trust identity data sent by the browser.
- Pin or verify SK's API TLS certificate as SK's docs require.
- Secrets/config in `backend/.env`: `SMARTID_ENV`, `SMARTID_RP_UUID`, `SMARTID_RP_NAME`, `SMARTID_HOST_URL`.
- From the result, store the personal code (isikukood), first name and last name. The personal code is
  sensitive: never log it, and show it only masked. The customer account is keyed on country + personal code.

### Test account (demo only)

- A fixed test customer for development and demos (a "Log in as test user" button).
- Enabled only when `SMARTID_ENV=demo` **and** `app.auth.test-account.enabled=true`.

### Guest checkout

Customers can still buy without logging in (the cart lives in the server-side session). Login gives
access to order history.

## Sessions and CSRF

- Server-side session (`JSESSIONID`), httpOnly, `SameSite=Lax`, and `Secure` in production. No tokens in `localStorage`.
- `GET /api/me` returns `{ type: "admin", name, email }` or 401. Customers will be added later.
- `POST /api/logout` ends the session.
- CSRF protection is on. The backend sets an `XSRF-TOKEN` cookie, and `frontend/src/lib/apiClient.ts`
  sends it back as the `X-XSRF-TOKEN` header on every non-GET request.
