import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'
import { realProductHandlers } from './realProductHandlers'

const realProducts = import.meta.env.VITE_REAL_PRODUCTS === 'true'

export const worker = setupWorker(
  ...(realProducts ? realProductHandlers : []),
  ...handlers,
)
