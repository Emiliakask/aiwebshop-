# Authentication

There are two completely separate login worlds. Never let one grant access to the other.

| Who | How | Status |
|---|---|---|
| Admin | Google OAuth (Gmail), allow-list | **Built** |
| Customer | Smart-ID "choose number" (personal code + notification), direct to SK | **Built** (verified against SK demo) |
| Customer | Smart-ID QR code (device link) | Planned (needs a QR library, ask first) |
| Customer (demo only) | Test account | **Built** |

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

1. **QR code** (device link, cross-device). The site shows a QR code that the backend
   regenerates every second, as SK requires. The user scans it with the Smart-ID app and confirms. The backend polls the session
   status from SK.
2. **Choose number** (notification-based with verification-code choice). The user enters their
   personal code (Estonia, Latvia or Lithuania). The Smart-ID app shows several numbers and the user picks the
   one our site displays. The backend polls the session status from SK.

Nothing else is allowed in production: no password login, no test account, no "skip".

### How it is built (`backend/src/main/java/com/webshop/auth/smartid/`)

SK Smart-ID **RP API v3**, called directly with Spring's `RestClient`, with no SK client library. Source of truth:
https://sk-eid.github.io/smart-id-documentation/rp-api/introduction.html (OpenAPI: `_/static/RP-API_V3.yml`).

| | Demo | Production |
|---|---|---|
| Base URL | `https://sid.demo.sk.ee/smart-id-rp/v3` | `https://rp-api.smart-id.com/v3` |
| RP UUID / name | `00000000-0000-4000-8000-000000000000` / `DEMO` (defaults) | `SMARTID_RP_UUID` / `SMARTID_RP_NAME` (required) |
| Scheme name in signed data | `smart-id-demo` (SK's docs only show `smart-id`; checked against the live demo) | `smart-id` |
| Trusted CAs (`resources/smartid/<env>/`) | TEST of SK ID Solutions ROOT G1E/G1R + TEST EID-Q 2024E/R | SK ID Solutions ROOT G1E/G1R + EID-Q 2024E/R, 2021E/R |

**Choose number** (notification flow):
1. `POST /api/auth/smart-id/notification { country, personalCode }`. The backend makes a fresh 64-byte `rpChallenge`,
   calls `POST /authentication/notification/etsi/PNO{country}-{code}` with the interaction
   `confirmationMessageAndVerificationCodeChoice`, and keeps the challenge in the server session. It answers
   `{ verificationCode }`: the 4 digits computed from SHA-256(rpChallenge), last 2 bytes, mod 10000.
2. The page shows the code. The Smart-ID app shows 3 codes and the user picks the same one.
3. The frontend polls `POST /api/auth/smart-id/status` about every 2 s (POST because a completed check logs the
   browser in, so it is CSRF-protected). The backend long-polls SK
   `GET /session/{id}?timeoutMs=1000` and answers `{ state: "running" }`, `{ state: "complete", me }` or
   `{ state: "failed", reason }` (`user_refused`, `timeout`, `wrong_vc`, `not_found`, `error`).

**Every OK response is verified before login** (SK "Response verification"):
- `state=COMPLETE`, `endResult=OK`, `signatureProtocol=ACSP_V2`, `flowType` is the one we started, and
  `interactionTypeUsed` is one we asked for.
- The certificate chains to **our configured CAs only** (no JVM trust store), is valid now, and passes an
  **OCSP revocation check** (`app.smartid.check-revocation`, on by default; off only in unit tests).
- It is a Smart-ID authentication certificate: policies `1.3.6.1.4.1.10015.17.2` + `0.4.0.2042.1.2`
  (qualified), key usage `digitalSignature` plus EKU `1.3.6.1.4.1.62306.5.7.0` (or the pre-2025
  clientAuth variant), and `cert.certificateLevel=QUALIFIED`.
- The subject `serialNumber` (`PNOEE-…`) equals the personal code that was entered.
- The RSASSA-PSS signature over
  `scheme|ACSP_V2|serverRandom|rpChallenge|userChallenge|b64(rpName)||b64(sha256(interactions))|interactionTypeUsed||flowType`
  is valid, with the hash, MGF1 and salt from the response.
- Only then: change the session id (keeps the cart), store the customer in the security context with
  `ROLE_CUSTOMER`, and drop the pending challenge.

**Test with SK demo accounts** (https://sk-eid.github.io/smart-id-documentation/test_accounts.html): in the demo
environment, choose number with EE **40504040001** completes automatically as "OK TEST".
30403039917 = user refused, 30403039983 = timeout, 30403039972 = wrong number chosen.

### Rules for the Smart-ID integration

- Never trust identity data sent by the browser. The identity comes only from the verified certificate.
- `sessionSecret` (QR flow) must never be sent to the frontend.
- Config in `backend/.env`: `SMARTID_ENV` (`demo`|`production`), `SMARTID_RP_UUID`, `SMARTID_RP_NAME`,
  `SMARTID_HOST_URL` (optional override), `SMARTID_TEST_ACCOUNT` (`true` only in demo).
  With `SMARTID_ENV=production` the app **refuses to start** if the test account is on, or the RP UUID/name
  are missing or still the demo values.
- Before going live: pin SK's production TLS certificate (SK "HTTPS pinning" page) and rate-limit login starts.
- From the result we keep country, personal code, first and last name in the session. The personal code is
  sensitive: never log it. `/api/me` only returns it masked (`4050404****`). Saving customers to the database
  (for order history) comes later.

### Test account (demo only)

- `POST /api/auth/test-account` logs in a fixed customer "Test User" (shown as a "Log in as test user" button).
- Works only when `SMARTID_ENV=demo` **and** `SMARTID_TEST_ACCOUNT=true`. Otherwise it answers 404 and the button is hidden
  (`GET /api/auth/smart-id/config` -> `{ environment, testAccount }`).

### Guest checkout

Customers can still buy without logging in (the cart lives in the server-side session). Login gives
access to order history.

## Sessions and CSRF

- Server-side session (`JSESSIONID`), httpOnly, `SameSite=Lax`, and `Secure` in production. No tokens in `localStorage`.
- `GET /api/me` returns `{ type: "admin", name, email }`, `{ type: "customer", name, country, personalCode /*masked*/ }` or 401.
- Customer login page: `/login` (`frontend/src/features/auth/components/CustomerLogin.tsx`). The header shows
  "Log in", or "Log out" when a customer is logged in.
- Logging in as a customer replaces an admin login in the same browser and vice versa: one session, one identity.
- `POST /api/logout` ends the session.
- CSRF protection is on. The backend sets an `XSRF-TOKEN` cookie, and `frontend/src/lib/apiClient.ts`
  sends it back as the `X-XSRF-TOKEN` header on every non-GET request.
