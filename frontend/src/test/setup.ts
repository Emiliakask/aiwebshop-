import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { resetMockDb } from '@/mocks/handlers'
import { server } from '@/mocks/server'

// jsdom doesn't implement scrolling (used by ScrollRestoration).
window.scrollTo = () => {}

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => resetMockDb())
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())
