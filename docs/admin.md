# Admin module

**Route:** `/admin` in the frontend (`frontend/src/pages/AdminPage.tsx`). Login is Google OAuth, admins only (see `auth.md`).

## Products: built

- List all products sorted by name, add, edit, delete (`features/admin/`).
- Product fields (`Product.java` = `productSchema`): `id` (UUID, server-generated), `slug` (unique,
  `[a-z0-9]+(-[a-z0-9]+)*`), `name`, `description`, `category`, `price` (`Money`), `imageUrl`,
  `imageAlt`, `stock` (≥ 0).
- The price is typed as text ("12.99") and converted to cents with integer maths (`toCents` / `fromCents`).
- Backend: `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}`. 409 if the slug is taken.
  These are **admin-only** (Google login + `ADMIN_EMAILS`, see `auth.md`). The frontend `/admin` page shows a
  "Log in with Google" link when `GET /api/me` returns 401.
- Prices are always EUR. The form has no currency choice.
- Products with stock 0 stay in the list (so the admin can restock them) but are unavailable in the shop.
- Ideas for later: image upload instead of URL, soft delete (products in past orders must stay readable;
  order lines keep a snapshot anyway), categories as their own table.

## Orders: planned

The admin sees all orders and whether they are **paid or not paid**.

Proposed API (add to the contract in `handlers.ts` when built):
```
GET /api/admin/orders?status=paid|pending_payment|payment_failed|cancelled&page=&search=
    -> { items: AdminOrderSummary[], total }
GET /api/admin/orders/{id}  -> AdminOrder (lines, customer, parcel machine, payment provider + reference, email sent?)
```

UI:
- A table with order number, date, customer, total, status badge (paid = green, pending = grey,
  failed/cancelled = red), payment provider and carrier.
- A status filter (default: all) and a search by order number / email, both kept in the URL query string.
- An order detail view. Read-only to start. Possible later actions: cancel an unpaid order, resend the email,
  mark as shipped.
- Handle loading, empty and error states like the rest of the app.
