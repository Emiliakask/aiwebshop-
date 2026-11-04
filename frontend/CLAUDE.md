# Webshop Frontend — Rules for Claude

React front end for a webshop. The backend lives in `../backend` and is the source of truth for
products, prices, stock, orders and users. This front end only displays data and collects input.

## Tech stack

Use these. Don't add another library that does the same job without asking first.

- **React 19 + TypeScript (strict mode)**, built with **Vite**
- **React Router** for routing
- **TanStack Query** for all server data (fetching, caching, mutations)
- **Zustand** for the small amount of client-only state (cart drawer open, UI preferences)
- **Tailwind CSS** for styling
- **React Hook Form + Zod** for forms and validation
- **Vitest + React Testing Library** for unit and component tests, **Playwright** for end-to-end tests
- **ESLint + Prettier**. Code must pass both.
- **MSW** (Mock Service Worker) for the fake API in development and tests

Package manager: **npm**. Commit `package-lock.json`.

## Commands

```bash
npm run dev         # start dev server
npm run build       # type-check + production build
npm run lint        # eslint
npm run format      # prettier --write
npm run test        # vitest
npm run test:e2e    # playwright
```

Before saying a task is done, run `npm run lint`, `npm run test` and `npm run build`, and report the results honestly.

## Folder structure

```
src/
  app/            # app entry, providers, router setup
  pages/          # one component per route (HomePage, ProductPage, CartPage, CheckoutPage, OrderPage)
  features/       # domain logic grouped by feature: products/, cart/, checkout/, auth/, account/
    <feature>/
      api.ts        # API calls + TanStack Query hooks for this feature
      types.ts      # types and Zod schemas
      components/   # components used only by this feature
  components/ui/  # shared, generic UI parts (Button, FormField, PriceTag, StatusMessage, ...)
  lib/            # helpers: apiClient.ts, money.ts (formatMoney), cn.ts, i18n/strings.ts
  hooks/          # shared hooks (usePageMeta)
  mocks/          # MSW fake backend: data.ts + handlers.ts (= the proposed API contract)
  test/           # test setup, renderApp() helper, app-level flow tests
  styles/
e2e/              # Playwright tests (run against the mock API)
```

- Put a component in `components/ui/` only if it has no webshop logic and at least two features use it.
- Use the `@/` import alias for `src/`. Don't write long `../../../` paths.

## Code style

- Use function components and hooks only. No class components.
- Use named exports. The one exception is route-level lazy pages, if the router needs a default export.
- Name files after their component: `ProductCard.tsx`. Use camelCase for hooks and utils: `useCart.ts`, `formatMoney.ts`.
- Don't use `any`. Use `unknown` and narrow it. Don't use `// @ts-ignore`.
- Keep components small. Past about 150 lines, split the component or pull the logic out into a hook.
- Write props as `type` aliases next to the component. Avoid `React.FC`.
- Don't leave `console.log` behind. Don't commit commented-out code.
- Match the style of the surrounding code over these rules if the two disagree.

## Data and API

- Make every HTTP call through `src/lib/apiClient.ts`. It handles the base URL, credentials, JSON
  and error normalisation. Never call `fetch` directly inside a component.
- Validate API responses with Zod schemas in `features/<x>/types.ts`. Bad data should fail loudly,
  not render as `undefined`.
- Read server data with TanStack Query (`useQuery` / `useMutation`). Don't copy server data into
  `useState` or Zustand.
- Keep query keys in one place per feature, e.g. `productKeys.detail(id)`.
- Take the API base URL from `import.meta.env.VITE_API_URL`. Never hardcode it.
- Every data view has to handle **loading, error and empty** states.

### Mock API

- When `VITE_ENABLE_MOCKS=true` (see `.env.example`), `npm run dev` serves fake responses from
  `src/mocks/handlers.ts`. Tests always use these handlers.
- The comment at the top of `handlers.ts` is the **proposed API contract** for `../backend`. When
  you add or change an endpoint, update the handler, the Zod schema and that comment together.
- To test an error case, override one handler with `server.use(...)` inside the test.

## Webshop rules (important)

### Money

- Prices are **integers in minor units** (cents) plus a currency code, e.g. `{ amount: 1999, currency: "EUR" }`.
- **Never use floating-point maths on money.** Don't do `price * 1.2` or `0.1 + 0.2`.
- Show prices only through `formatMoney()` (built on `Intl.NumberFormat`). Don't build strings like `"€" + price`.

### Trust boundaries

- **The frontend never decides the price, discount, tax, shipping cost or stock.** It shows what
  the backend returns. Totals shown in the cart are only a preview. The backend recalculates
  everything at checkout.
- Send product IDs and quantities to the backend, never prices.
- Client-side validation is there for UX only. The backend validates again.

### Cart

- The cart is stored on the server (and keyed to a guest session for anonymous users). Keep a local
  copy only as a cache.
- Quantity changes may use optimistic updates, but they must roll back if the request fails.
- Handle out-of-stock items and price changes between adding to cart and checkout, and tell the user.

### Checkout and payments

- **Never handle raw card data.** Use the payment provider's hosted fields or redirect (e.g. Stripe Elements / Checkout).
- Disable the "Place order" button while the order is submitting, so double clicks can't create two orders.
  Send an idempotency key with order creation.
- After a payment redirect, check the order status with the backend. Don't assume a success page means the order was paid.

### Auth and security

- Use httpOnly cookies set by the backend for auth. **Don't store tokens in `localStorage`.**
- Never put secrets in frontend code or in `VITE_*` env vars. Everything in the bundle is public.
- Don't use `dangerouslySetInnerHTML`. If product descriptions contain HTML, sanitise them with DOMPurify first.
- Guard protected routes (account, order history), but remember the real protection is on the backend.

## UX, accessibility, performance

- Use semantic HTML: real `<button>`, `<a>`, `<form>`, `<label>`. Don't put `onClick` on a `<div>`.
- Every interactive element must work with a keyboard and show a visible focus state.
- Every product image needs meaningful `alt` text.
- Aim for WCAG 2.1 AA colour contrast.
- Build mobile-first and responsive. Most shoppers are on phones.
- Lazy-load route pages and below-the-fold images. Give images `width`/`height` (or `aspect-ratio`) so the layout doesn't jump.
- Use skeleton loaders for product grids instead of a spinner over the whole page.
- Keep shop state that the user would want to share or bookmark (filters, sorting, pagination, search) in the URL query string.
- Set a page `<title>` and meta description for product and category pages.
- Put user-facing text in one place (e.g. `src/lib/i18n` or constants) so it can be translated later.

## Testing

- Test what the user sees and does (RTL queries by role or label), not implementation details.
- Must have tests: `formatMoney`, cart logic, form validation, and checkout flow components.
- Must have Playwright e2e tests for the critical path: browse → product → add to cart → checkout.
- Mock the API with **MSW** in tests. Don't mock `fetch` by hand.

## Working agreements for Claude

- Ask before adding a new dependency, and say why it's needed.
- Ask before changing the API contract with the backend. Don't invent endpoints silently. If an
  endpoint doesn't exist yet, say so and suggest its shape.
- Keep changes focused on the task. Don't refactor unrelated code without asking.
- Never commit `.env` files. Keep `.env.example` up to date when adding env vars.
- Prefer clear, readable code over clever code. This project values maintainability.
