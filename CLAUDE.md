# Webshop — Project Rules for Claude

An Estonian webshop. Customers browse products, add them to a cart, pick a parcel machine, pay
through a payment provider, and then the customer **and** the admin get an email that says whether the
order was **paid or not paid**. An admin module lets the shop owner manage products and see orders.

```
webshop/                 ← git repository root (frontend, backend and docs in one repo)
  frontend/   React 19 + TypeScript + Vite. Has its own rules: frontend/CLAUDE.md
  backend/    Spring Boot 4 + Java 21 + PostgreSQL. Has its own rules: backend/CLAUDE.md
  docs/       Design docs for each domain. Read the relevant one before working in that area.
```

## Core principles (apply everywhere)

1. **The backend is the source of truth.** It owns prices, stock, totals, shipping cost, order
   status and payment status. The frontend displays them and collects input. Never trust a price,
   a total or a "paid" flag that comes from the browser.
2. **The shop sells in euros only.** Every amount is `{ amount: <integer cents>, currency: "EUR" }`, in
   Java (`Money` record), TypeScript (`moneySchema`) and the database. The backend rejects any other
   currency and any non-integer amount (e.g. `12.99` sent as `amount` is a 400, not silently truncated).
   **No floating point and no division on money.** Only `formatMoney()` divides, and only for display.
3. **Out of stock means unavailable.** A product with `stock = 0` shows as unavailable on the
   products page and can't be added to the cart. Checkout is refused (frontend and **backend**) while any
   cart line has stock 0 or more quantity than is in stock.
4. **Payment providers and parcel machine carriers are plugins.** EveryPay (LHV) is the first
   payment provider, and Omniva / SmartPost / DPD / Unisend are the first carriers, but others will
   be added (Montonio, Maksekeskus, TarkPay, Stripe, Revolut, ...). Business code depends only on the
   `PaymentProvider` / `ShippingCarrier` interfaces. See [docs/payments.md](docs/payments.md) and
   [docs/shipping.md](docs/shipping.md).
5. **Payment status comes from the provider, server-to-server.** An order becomes `paid` only after
   the backend verifies it with the provider (webhook or status API call). A user landing on a
   "success" URL proves nothing.
6. **Two separate login worlds** (see [docs/auth.md](docs/auth.md)):
   - **Admins** log in with **Google OAuth (Gmail)**, limited to the allow-list in `ADMIN_EMAILS`.
   - **Customers** log in with **Smart-ID, connected directly to SK**. It has two environments:
     - **production**: only the QR code or choose-number flows.
     - **demo**: SK demo accounts plus our test account.
7. **Every order outcome (paid / not paid) is emailed to both the customer and the admin.**
   See [docs/orders-and-email.md](docs/orders-and-email.md).
8. **Secrets live only in `.env` files, and `.env` files never reach GitHub.**
   - Backend secrets (DB password, Google OAuth, Smart-ID, payment keys, SMTP) go in `backend/.env`.
     Spring loads it through `spring.config.import`. `application.properties` only references them
     as `${VAR}`.
   - Frontend config goes in `frontend/.env.local`. Nothing secret ever goes in `VITE_*`, because
     those values end up in the public bundle.
   - Only `.env.example` files are committed, and they hold placeholder values. When you add a
     variable, add it to the matching `.env.example`.
   - Before committing, check `git status` for env files and never use `git add -f` on them.

## Domain docs

| Area | Doc | Status |
|---|---|---|
| Products & admin module | [docs/admin.md](docs/admin.md) | Product CRUD built and admin-only; orders view planned |
| Orders, statuses, emails | [docs/orders-and-email.md](docs/orders-and-email.md) | Order creation built; emails planned |
| Payments | [docs/payments.md](docs/payments.md) | Planned (EveryPay first) |
| Shipping / parcel machines | [docs/shipping.md](docs/shipping.md) | Omniva built; SmartPost, DPD, Unisend planned |
| Authentication | [docs/auth.md](docs/auth.md) | Admin Google login built; Smart-ID choose-number + demo test account built; QR planned |
| API contract | `frontend/src/mocks/handlers.ts` (comment at the top) | Source of truth for endpoint shapes |

## Current state (keep this up to date)

- **Built in the backend:**
  - products: public reads, and admin-only writes
  - admin login with Google plus the `ADMIN_EMAILS` allow-list (`GET /api/me`, `POST /api/logout`)
  - customer login with Smart-ID "choose number", directly to SK RP API v3 (`/api/auth/smart-id/**`), every
    response verified (signature, CA chain, OCSP, identity); demo/production split; demo test account
  - CSRF protection
  - session cart (`/api/cart/**`)
  - orders (`POST /api/orders` with an idempotency key, server-side totals and a locked stock check; `GET /api/orders/{id}`)
  - Omniva parcel machines (`/api/shipping/methods`, `/api/shipping/pickup-points`): live list cached for 12 h, and the chosen machine checked and saved on the order
  - EUR-only money with strict integer amounts
  - config read from `backend/.env`
- **Built in the frontend:**
  - shop, cart and checkout, all wired to the API contract; checkout has a searchable parcel machine picker
  - the admin page behind a Google login
  - `/login`: Smart-ID choose-number login (shows the verification code) and the demo test-account button
  - order page with payment-status polling
  - sold-out products shown as unavailable
- **Not built yet:**
  - payments (orders stay `pending_payment`)
  - emails
  - Smart-ID QR code login (needs a QR library); saving customers to the database / order history
  - SmartPost, DPD and Unisend; registering shipments with the carrier
  - admin orders view
- **Known gaps:**
  - `ddl-auto=update` never removes or relaxes columns. On 2026-11-04 the local `orders` table was changed by hand
    (old address columns made optional, delivery columns added) when checkout switched to parcel machines.
    Move to Flyway migrations before the first real deployment.

## Running locally

```bash
# Backend (needs PostgreSQL on localhost:5432, database "webshop", and backend/.env, see backend/.env.example)
cd backend && ./mvnw spring-boot:run      # http://localhost:3000/api
cd backend && ./mvnw test                 # uses in-memory H2, no Postgres needed

# Frontend
cd frontend && npm run dev                # http://localhost:5173
```

Frontend mock modes (`frontend/.env.local`):
- `VITE_ENABLE_MOCKS=true`: everything is fake, no backend needed (you're shown as logged in as admin).
- `VITE_ENABLE_MOCKS=true` + `VITE_REAL_PRODUCTS=true`: products and admin login come from the real backend; cart and orders stay mocked.
- `VITE_ENABLE_MOCKS=false`: everything goes to the real backend.

## Working agreements for Claude

- **The API contract is shared.** When an endpoint is added or changed, update the backend
  controller, the MSW handler, the Zod schema and the contract comment in `handlers.ts` in the
  same change. Ask before changing an existing endpoint's shape.
- When moving a feature from "mocked" to "real backend", keep the mock working, because tests depend on it.
- Ask before adding a dependency (Maven or npm) and say why it's needed.
- Ask before picking a concrete external service that costs money or needs a contract.
- Before saying a task is done, run the checks for every part you touched:
  frontend `npm run lint && npm run test && npm run build`; backend `./mvnw test`. Report the results honestly.
- Keep changes focused. Update the "Current state" section above when something moves from planned to built.
- Put all user-facing text in `frontend/src/lib/i18n/strings.ts`, never inline in components. The
  text there is currently English. Don't translate it unless asked.
