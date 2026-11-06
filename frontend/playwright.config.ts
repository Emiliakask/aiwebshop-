import { defineConfig, devices } from '@playwright/test'

// Own port, so a dev server you have running (maybe against the real backend) is never reused by mistake.
const PORT = Number(process.env.E2E_PORT ?? 5199)

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  webServer: {
    // Runs against the MSW mock API so e2e tests don't need the backend.
    command: `npm run dev -- --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env.CI,
    // Fully mocked, whatever .env.local says (e.g. VITE_REAL_PRODUCTS).
    env: { VITE_ENABLE_MOCKS: 'true', VITE_REAL_PRODUCTS: 'false' },
  },
})
