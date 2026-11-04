# Webshop — Frontend

React + TypeScript storefront: product list with search and sorting, product pages, cart and checkout.

## Getting started

```bash
npm install
cp .env.example .env.local   # already done if .env.local exists
npm run dev
```

Open http://localhost:5173.

The backend (`../backend`) isn't built yet. With `VITE_ENABLE_MOCKS=true` in `.env.local`, the app
uses a fake API (`src/mocks/`) that runs in the browser, so the whole shop works without a backend.
Set it to `false` once the real backend is running at `VITE_API_URL`.

## Scripts

| Command            | What it does                                     |
| ------------------ | ------------------------------------------------ |
| `npm run dev`      | Start the dev server                             |
| `npm run build`    | Type-check and build for production into `dist/` |
| `npm run preview`  | Serve the production build locally               |
| `npm run lint`     | Run ESLint                                       |
| `npm run format`   | Format all files with Prettier                   |
| `npm test`         | Run unit and component tests (Vitest)            |
| `npm run test:e2e` | Run browser tests (Playwright, desktop + mobile) |

The first time you run e2e tests on a new machine: `npx playwright install chromium`.

## API

The API the frontend expects is described at the top of
[`src/mocks/handlers.ts`](src/mocks/handlers.ts), and the response shapes are the Zod schemas in
`src/features/*/types.ts`. Use those as the spec when building the backend.

## Project rules

See [`CLAUDE.md`](CLAUDE.md) for the stack, folder structure and coding rules.
