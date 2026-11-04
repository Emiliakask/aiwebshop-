# Webshop Backend — Rules for Claude

Spring Boot REST API for the webshop. It is the **source of truth** for products, prices, stock,
carts, orders, payments and users. Project-wide rules are in `../CLAUDE.md`, and domain designs are in `../docs/`.

## Tech stack

- **Java 21**, **Spring Boot 4** (`webmvc`, `data-jpa`, `validation`, `actuator`, `security`, `oauth2-client`)
- **PostgreSQL** (`localhost:5432/webshop` in dev). Tests use in-memory **H2** in PostgreSQL mode.
- Maven wrapper (`./mvnw`). Don't require a global Maven install.
- Planned additions (ask before adding): `spring-boot-starter-mail` (order emails), Flyway (migrations).

## Commands

```bash
cp .env.example .env     # once, then fill in real values (never commit .env)
./mvnw spring-boot:run   # API on http://localhost:3000/api
./mvnw test              # H2, no Postgres or .env needed
```

## Configuration and secrets

- `application.properties` imports `optional:file:.env[.properties]` and references secrets only as
  `${DB_PASSWORD}`, `${GOOGLE_CLIENT_SECRET}` and so on. **Never write a real secret into a committed file.**
- Every variable is listed with a placeholder in `.env.example`. Keep it complete.
- Tests use `src/test/resources/application.properties` with dummy values, so they don't need `.env`.

## Package structure

Group by feature (domain), not by layer.

```
com.webshop
  common/          Money (EUR, integer cents), JSON error handling
  products/        Product, ProductController, ProductRepository
  auth/            SecurityConfig, admin Google login + allow-list, /api/me, CSRF cookie
  cart/            session cart (CartController, CartService, Cart DTOs)
  orders/          Order, OrderLine, OrderStatus, OrderService, OrderController
  payments/        PaymentProvider interface + one sub-package per provider   (planned)
  shipping/        ShippingCarrier interface + one sub-package per carrier    (planned)
  email/           OrderEmailService (customer + admin)                      (planned)
  admin/           /api/admin/** controllers (orders view)                   (planned)
```

- A provider sub-package (e.g. `payments/everypay`) may only be used through its interface.
- Keep controllers thin. Put business rules in a `*Service`.

## API conventions

- All routes start with `/api`. Admin-only routes go under `/api/admin/**`, or are product writes.
- Errors: non-2xx with JSON `{ "message": "..." }` (the frontend's `apiClient` reads it).
  Throw `ResponseStatusException` with a clear message.
- Status codes: 400 for invalid input, 401 when not logged in, 403 when not allowed, 404 for not found,
  409 for conflicts (slug taken, out of stock), 201 for created, 204 for delete.
- Validate request bodies with `@Valid` and Jakarta constraints. Re-validate everything.
- Never let the client set server-owned fields (`id`, prices on orders, `status`, totals).
- Response shapes must match the Zod schemas in `frontend/src/features/*/types.ts` and the contract
  comment in `frontend/src/mocks/handlers.ts`. Change those together.

## Money (EUR only)

- `Money(Long amount, String currency)` holds integer cents. `currency` must be `"EUR"` (validated).
- JSON numbers with a fraction are **rejected** for integer fields
  (`spring.jackson.deserialization.accept-float-as-int=false`), so `12.99` can't become `12`.
- Arithmetic uses `Math.multiplyExact` / `Math.addExact`. No `double`, `float` or division.

## Orders and payments (rules that must never break)

- `POST /api/orders` requires an `Idempotency-Key` header. The same key always returns the same order.
- Lock the product rows (`PESSIMISTIC_WRITE`), check stock and decrement it in the same transaction that creates the order.
  Refuse with 409 if any line has stock 0 or too little stock.
- Only `PaymentService` changes an order's payment status, and only after verifying with the provider.
- A status change to `paid` or `payment_failed` triggers one email to the customer **and** one to the admin.

## Security

- Spring Security: the session cookie is httpOnly with `SameSite=Lax`. CSRF uses the `XSRF-TOKEN`
  cookie and the `X-XSRF-TOKEN` header. Unauthenticated API calls get 401 JSON, never a redirect.
- `ROLE_ADMIN` is only given to Google accounts in `ADMIN_EMAILS` with a verified email.
- CORS allows only `FRONTEND_URL`, with credentials, and never `*`.
- Don't log card data, full personal codes (isikukood) or secrets.

## Testing

- Use `@SpringBootTest` + `@AutoConfigureMockMvc` against H2 for API tests, and `spring-security-test`
  (`.with(csrf())`, `.with(oidcLogin()...)`) for auth.
- Must have tests: admin-only product writes, money validation (EUR, integer), cart, order totals,
  out-of-stock refusal, idempotency.
- Never call real provider APIs (Google, SK, payment providers) from tests.
