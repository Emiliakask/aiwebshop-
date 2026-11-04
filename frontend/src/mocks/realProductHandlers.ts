import { bypass, http, passthrough } from 'msw'
import {
  productListSchema,
  productSchema,
  type Product,
} from '@/features/products/types'
import { rememberProducts } from './handlers'

/**
 * Sends product requests to the real backend while cart and orders stay mocked.
 * The fetched products are copied into the fake database so the fake cart can find them.
 */
async function forward(
  request: Request,
  toProducts: (data: unknown) => Product[],
) {
  const response = await fetch(bypass(request))
  if (response.ok) {
    rememberProducts(toProducts(await response.clone().json()))
  }
  return response
}

export const realProductHandlers = [
  http.get('*/api/products', ({ request }) =>
    forward(request, (data) => productListSchema.parse(data).items),
  ),
  http.get('*/api/products/:slug', ({ request }) =>
    forward(request, (data) => [productSchema.parse(data)]),
  ),
  // Admin login and changes go straight to the backend (log in via Google
  // there first); the next list fetch picks the changes up.
  http.get('*/api/me', () => passthrough()),
  http.post('*/api/logout', () => passthrough()),
  http.post('*/api/products', () => passthrough()),
  http.put('*/api/products/:id', () => passthrough()),
  http.delete('*/api/products/:id', () => passthrough()),
]
