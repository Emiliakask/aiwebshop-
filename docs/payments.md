# Payments

**Status:** **EveryPay built** and tested against the EveryPay/LHV demo (card paid, card declined).
Code: `backend/src/main/java/com/webshop/payments/`. The frontend mock still simulates a provider that confirms
payment 3 s after the order is placed (`PAYMENT_CONFIRMATION_MS` in `frontend/src/mocks/handlers.ts`), with `paymentUrl` `null`.

## EveryPay setup (only these values change between test and live)

In `backend/.env` (git-ignored; placeholders in `backend/.env.example`):

| Variable | Demo (test) | Production |
|---|---|---|
| `EVERYPAY_API_URL` | `https://igw-demo.every-pay.com/api/v4` | `https://pay.every-pay.eu/api/v4` |
| `EVERYPAY_API_USERNAME` / `EVERYPAY_API_SECRET` | demo merchant portal > General settings | live merchant portal > General settings |
| `EVERYPAY_ACCOUNT_NAME` | `EUR3D1` | your EUR processing account |
| `PUBLIC_URL` | `http://localtest.me:3000` | `https://your-shop-domain` |

- `PUBLIC_URL` is where EveryPay sends the customer back (`{PUBLIC_URL}/api/payments/everypay/return`).
  EveryPay **refuses `localhost` and IP addresses**; `localtest.me` is a public name that points to 127.0.0.1.
- Callback URL to set in the EveryPay merchant portal (production): `{PUBLIC_URL}/api/payments/everypay/notify`.
  Locally EveryPay can't reach your machine, so the order page's polling asks EveryPay instead.
- Test cards (LHV sandbox, any name, expiry 08/27): `5168830788067541` CVC `973` = paid,
  `5413330089010020` CVC `111` = declined. On the 3-D Secure test screen click **Yes**.

### How it works (built)

1. `POST /api/orders` saves the order (stock reserved), then `PaymentService.startFor` calls EveryPay
   `POST /payments/oneoff` (amount = total cents as an exact 2-decimal number, `order_reference` = order id)
   and returns the order with `paymentUrl`. The checkout page redirects there.
   If EveryPay refuses, the order becomes `payment_failed` and the stock is released.
2. EveryPay sends the customer to `/api/payments/everypay/return?payment_reference=…`. The backend asks EveryPay
   `GET /payments/{ref}` and redirects to `{FRONTEND_URL}/orders/{id}`. The return URL itself proves nothing.
3. `GET /api/orders/{id}` (polled by the order page) also asks EveryPay, at most every 3 s per payment.
4. `settled`/`authorised` -> `paid` (only if EveryPay's `initial_amount` on **our** account equals the order total;
   otherwise logged as an error and left pending). `failed`/`abandoned`/`voided` -> `payment_failed` + stock back.
   Each change happens once, with the order row locked.

## Goal

Payment providers can be swapped and combined. The first one is **EveryPay (LHV)**. Next likely
candidates are **Montonio, Maksekeskus, TarkPay**, and international ones like **Stripe** and
**Revolut**. Adding a provider must not require changes to order, checkout or frontend code.

## Design

### Backend interface (`com.webshop.payments`)

```java
public interface PaymentProvider {
    /** Stable id stored on payments and used in URLs/config: "everypay", "montonio", "stripe", ... */
    String code();

    /** Create a payment at the provider and return where to send the customer. */
    PaymentSession start(Order order, PaymentUrls urls);   // urls: customer return URL + webhook URL

    /** Verify and parse a provider callback/webhook (signature check happens here). */
    PaymentResult handleNotification(HttpServletRequest request);

    /** Ask the provider directly for the current status (used after redirects and by a reconciliation job). */
    PaymentResult fetchStatus(String providerReference);
}
```

- `PaymentSession`: `{ providerReference, redirectUrl }`
- `PaymentResult`: `{ providerReference, status: PAID | FAILED | PENDING | CANCELLED, raw details for logging }`
- `PaymentProviderRegistry` collects all `PaymentProvider` beans and looks one up by `code()`.
- Which providers are **enabled**, and in what order they are shown, comes from config:
  ```properties
  app.payments.enabled=everypay
  # later: app.payments.enabled=everypay,montonio,stripe
  ```
  Each adapter has its own `app.payments.<code>.*` settings, with secrets taken from env vars.

### Not built yet

- Payment method choice at checkout (`GET /api/payment-methods`): with one provider the backend uses the first in
  `app.payments.enabled`. Add it together with the second provider.
- The reconciliation job: orders whose customer never comes back stay `pending_payment` (stock reserved) until
  someone opens the order page. Build the scheduled check + 30 min expiry before going live.
- Emails on paid / not paid (`orders-and-email.md`).

### Data

A `Payment` record is stored separately from the order (one order may have several attempts):
`id, orderId, providerCode, providerReference, status, amount (Money), createdAt, updatedAt`.

### Flow

1. The customer picks a payment method on checkout. The list comes from `GET /api/payment-methods`
   (`[{ code, name, logoUrl }]`), so **the frontend never hardcodes providers**.
2. `POST /api/orders` (with `paymentMethod` code and `Idempotency-Key`) creates the order with
   status `pending_payment`, calls `provider.start(...)` and returns the order with `paymentUrl`.
3. The frontend redirects to `paymentUrl` (the provider's hosted page). **We never touch card data.**
4. The provider calls our webhook `POST /api/payments/{code}/notify`. The backend verifies the
   signature (or re-fetches the status with `fetchStatus`), then updates the payment and order status.
5. The provider redirects the customer back to `/orders/{id}`. The order page polls
   `GET /api/orders/{id}` while the status is `pending_payment` (already implemented in `useOrder`).
6. When the status becomes `paid` or `payment_failed`, emails go to the customer and the admin (see `orders-and-email.md`).

### Rules

- The shop charges in **EUR only**. International providers (Stripe, Revolut) are also charged in EUR;
  any currency conversion is the customer's bank's business, not ours.
- **Never** mark an order paid from the return URL or its query params. Only use a verified webhook or `fetchStatus`.
- Webhooks are idempotent: the same notification twice must not change anything or send a second email.
- Check that the amount and currency reported by the provider equal the order total. If they don't
  match, don't mark the order paid; log it and flag the order.
- Run a scheduled reconciliation job that calls `fetchStatus` for payments stuck in `PENDING`, and
  expires them to `payment_failed` after a timeout (e.g. 30 min, configurable).
- Use each provider's **test/sandbox** environment outside production.

## Providers

| Code | Provider | Type | Notes |
|---|---|---|---|
| `everypay` | EveryPay (LHV) | cards + bank links | **Built.** Hosted payment page; verified via status API on return, callback and order-page polling. |
| `montonio` | Montonio | bank links, cards, BNPL | Planned |
| `maksekeskus` | Maksekeskus (MakeCommerce) | bank links, cards | Planned |
| `tarkpay` | TarkPay | | Planned |
| `stripe` | Stripe | international cards/wallets | Planned. Stripe Checkout (hosted). |
| `revolut` | Revolut Business | international | Planned |

Look up each provider's current API docs when implementing an adapter. Don't rely on memory for endpoint names or signature schemes.
