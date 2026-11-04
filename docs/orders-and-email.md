# Orders and emails

**Status:** the backend builds orders from the server-side cart (`POST /api/orders`). Payment and emails are planned,
so orders currently stay `pending_payment`. The frontend mock (`frontend/src/mocks/handlers.ts`)
still simulates payment for development and tests.

## Order statuses

Defined in `frontend/src/features/checkout/types.ts` (`orderStatusSchema`) and `com.webshop.orders.OrderStatus`.

```
pending_payment ──► paid
       │
       ├──────────► payment_failed   (provider says failed, or timeout expired)
       └──────────► cancelled        (admin or customer cancels before payment)
```

- New orders start as `pending_payment`.
- Only `PaymentService` moves an order to `paid` / `payment_failed` (see `payments.md`).
- `paid` is final for payment purposes. Refunds, if ever needed, get their own statuses; don't reuse these.
- On `payment_failed` / `cancelled`, release the reserved stock back.

## Creating an order (rules)

- The checkout sends **only** contact and shipping details plus an `Idempotency-Key` header. The
  products and quantities come from the server-side cart, and the prices from the database.
- The order is refused (409) if the cart is empty or **any item has stock 0 or less stock than
  requested**. Stock is checked and decremented in the same transaction, with the product rows locked.
- Totals are integer cents in EUR: `lineTotal = unitPrice × quantity` (`Math.multiplyExact`),
  `subtotal = Σ lineTotal`, shipping €4.99 (free from €50.00), `total = subtotal + shipping`.
  No division, no floating point, so no rounding.
- The same `Idempotency-Key` always returns the same order.
- Only the browser session that placed the order, or an admin, can read it (`GET /api/orders/{id}`).

## Order data

Snapshot everything at order time, so later product edits don't change past orders:
`id` (human-readable, e.g. `WS-123456`), `status`, `email`, customer name, address, lines
(`productId, name, unitPrice, quantity, lineTotal`), `subtotal`, `shippingPrice`, `total` (all `Money`, EUR),
payment provider code (later), parcel machine snapshot (later), `createdAt`, `paidAt`.

## Emails: to the customer and to the admin

Every outcome is emailed to **both the customer and the admin(s)**:

| Trigger | Customer gets | Admin gets |
|---|---|---|
| Status → `paid` | **Order confirmed / paid**: order number, lines, total, delivery, link to `/orders/{id}` | **New paid order**: order number, customer, total, link to the admin order view |
| Status → `payment_failed` | **Payment not completed**: order number, total, how to try again / contact us | **Order not paid**: order number, customer, total |

- Admin recipients: `ADMIN_NOTIFY_EMAILS` in `backend/.env` (falls back to `ADMIN_EMAILS`).
- Send from the backend after the status change transaction commits (e.g.
  `@TransactionalEventListener(phase = AFTER_COMMIT)`), never from the frontend.
- Idempotent: record `customerEmailSentAt` / `adminEmailSentAt` on the order and never send twice,
  even if the webhook repeats.
- If sending fails, log it and retry later. A failed email must never roll back the payment status.
- Email text uses the same language as the shop UI (currently English). Use HTML + plain-text versions.
- SMTP settings and credentials come from `backend/.env` (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`,
  `MAIL_PASSWORD`, `MAIL_FROM`).
- In dev, use a local mail catcher (e.g. Mailpit) so no real emails go out.
