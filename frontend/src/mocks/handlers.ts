/**
 * Fake backend for development and tests.
 *
 * These handlers are also the PROPOSED API CONTRACT for ../backend:
 *
 *   GET    /api/products?search=&sort=     -> { items: Product[], total }
 *   GET    /api/products/:slug             -> Product | 404
 *   POST   /api/products          [admin]  ProductInput -> 201 Product | 400 | 401 | 403 | 409 (slug taken)
 *   PUT    /api/products/:id      [admin]  ProductInput -> Product | 400 | 401 | 403 | 404 | 409
 *   DELETE /api/products/:id      [admin]  -> 204 | 401 | 403 | 404
 *   GET    /api/me                         -> Me (admin | customer) | 401
 *   POST   /api/logout                     -> 204
 *   GET    /api/auth/smart-id/config       -> { environment: "demo"|"production", testAccount }
 *   POST   /api/auth/smart-id/notification { country, personalCode } -> { verificationCode } | 400 | 404 (no account) | 502
 *   POST   /api/auth/smart-id/status       -> { state: "running" } | { state: "complete", me: Customer }
 *                                             | { state: "failed", reason } | 404 (no login in progress)
 *   POST   /api/auth/test-account          -> Customer | 404 (not demo / disabled)
 *   GET    /api/cart                       -> Cart            (session cookie)
 *   POST   /api/cart/items                 { productId, quantity } -> Cart | 400 | 404 | 409 (out of stock)
 *   PATCH  /api/cart/items/:productId      { quantity }            -> Cart | 400 | 404
 *   DELETE /api/cart/items/:productId      -> Cart
 *   GET    /api/shipping/methods?country=EE                -> ShippingMethod[] (priced for this cart)
 *   GET    /api/shipping/pickup-points?carrier=&country=   -> PickupPoint[] (sorted by city, name) | 400
 *   POST   /api/orders                     CheckoutFormValues + Idempotency-Key header
 *                                          -> 201 Order (200 for a repeated key)
 *                                             | 400 (invalid, unknown parcel machine) | 409 (item unavailable)
 *   GET    /api/orders/:id                 -> Order | 404 (also 404 if it isn't this session's order)
 *
 * Shapes are the Zod schemas in src/features/<feature>/types.ts.
 * Money is always { amount: <integer euro cents>, currency: "EUR" }.
 * Errors: non-2xx with { message: string }.
 * Non-GET requests send the XSRF-TOKEN cookie back as the X-XSRF-TOKEN header.
 * Admin login itself is a browser redirect to {backend}/oauth2/authorization/google.
 * Mock Smart-ID personal codes: 30403039917 refuses, 39001010002 has no account, anything else
 * valid logs in as "OK TEST" after two status checks.
 */
import { delay, http, HttpResponse } from 'msw'
import type { Cart } from '@/features/cart/types'
import { MAX_QUANTITY_PER_ITEM } from '@/features/cart/types'
import { SHOP_CURRENCY, type Money } from '@/lib/money'
import type { Customer, Me } from '@/features/auth/types'
import type { Order } from '@/features/checkout/types'
import {
  parseProductSort,
  productSchema,
  type Product,
} from '@/features/products/types'
import { pickupPoints, products as seedProducts } from './data'

const CURRENCY = SHOP_CURRENCY
/** Omniva parcel machine price; free from €50 (same rule as the backend). */
const SHIPPING_FEE = 499
const FREE_SHIPPING_FROM = 5000
const CARRIERS = [
  {
    carrierCode: 'omniva',
    name: 'Omniva parcel machine',
    countries: ['EE', 'LV', 'LT'],
  },
]
/** Mock "payment provider" confirms payment this long after the order is placed. */
const PAYMENT_CONFIRMATION_MS = 3000

type StoredOrder = Order & { createdAt: number }

let products: Product[] = []
let cart = new Map<string, number>()
let orders = new Map<string, StoredOrder>()
let idempotencyKeys = new Map<string, string>()
/** The mock shop starts logged in as admin, so /admin works without Google. */
let me: Me | null = null
let smartIdLogin: { personalCode: string; polls: number } | null = null
const MOCK_ADMIN: Me = {
  type: 'admin',
  name: 'Mock Admin',
  email: 'admin@example.com',
}

/** Resets the fake database. Called once on start and before each test. */
export function resetMockDb() {
  products = seedProducts.map((p) => ({ ...p }))
  cart = new Map()
  orders = new Map()
  idempotencyKeys = new Map()
  me = MOCK_ADMIN
  smartIdLogin = null
}

const MOCK_CUSTOMER: Customer = {
  type: 'customer',
  name: 'OK TEST',
  country: 'EE',
  personalCode: '4050404****',
}
resetMockDb()

/** Lets the fake cart use products that came from the real backend (see realProductHandlers.ts). */
export function rememberProducts(items: Product[]) {
  for (const item of items) {
    products = [...products.filter((p) => p.id !== item.id), item]
  }
}

function findProduct(productId: string) {
  return products.find((p) => p.id === productId)
}

function money(amount: number): Money {
  return { amount, currency: CURRENCY }
}

function buildCart(): Cart {
  const items: Cart['items'] = []
  for (const [productId, quantity] of cart) {
    const product = findProduct(productId)
    if (!product) continue
    items.push({
      productId,
      slug: product.slug,
      name: product.name,
      imageUrl: product.imageUrl,
      imageAlt: product.imageAlt,
      unitPrice: product.price,
      quantity,
      lineTotal: money(product.price.amount * quantity),
      stock: product.stock,
      issue:
        product.stock === 0
          ? 'out_of_stock'
          : quantity > product.stock
            ? 'insufficient_stock'
            : null,
    })
  }
  return {
    items,
    itemCount: items.reduce((sum, i) => sum + i.quantity, 0),
    subtotal: money(items.reduce((sum, i) => sum + i.lineTotal.amount, 0)),
  }
}

function error(status: number, message: string) {
  return HttpResponse.json({ message }, { status })
}

function requireAdmin() {
  if (!me) return error(401, 'Not logged in')
  return me.type === 'admin' ? null : error(403, 'Forbidden')
}

const productInputSchema = productSchema.omit({ id: true })

function isSlugTaken(slug: string, exceptId?: string) {
  return products.some((p) => p.slug === slug && p.id !== exceptId)
}

function isPositiveInt(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
}

function withPaymentStatus(order: StoredOrder): Order {
  const { createdAt, ...rest } = order
  if (
    rest.status === 'pending_payment' &&
    Date.now() - createdAt >= PAYMENT_CONFIRMATION_MS
  ) {
    order.status = 'paid'
    rest.status = 'paid'
  }
  return rest
}

export const handlers = [
  http.get('*/api/products', async ({ request }) => {
    await delay()
    const url = new URL(request.url)
    const search = url.searchParams.get('search')?.toLowerCase().trim() ?? ''
    const sort = parseProductSort(url.searchParams.get('sort'))

    const items = products.filter(
      (p) =>
        !search ||
        p.name.toLowerCase().includes(search) ||
        p.category.toLowerCase().includes(search),
    )
    if (sort === 'price-asc')
      items.sort((a, b) => a.price.amount - b.price.amount)
    if (sort === 'price-desc')
      items.sort((a, b) => b.price.amount - a.price.amount)
    if (sort === 'name') items.sort((a, b) => a.name.localeCompare(b.name))

    return HttpResponse.json({ items, total: items.length })
  }),

  http.get('*/api/products/:slug', async ({ params }) => {
    await delay()
    const product = products.find((p) => p.slug === params.slug)
    return product
      ? HttpResponse.json(product)
      : error(404, 'Product not found')
  }),

  http.get('*/api/me', async () => {
    await delay()
    return me ? HttpResponse.json(me) : error(401, 'Not logged in')
  }),

  http.post('*/api/logout', async () => {
    await delay()
    me = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/auth/smart-id/config', async () => {
    await delay()
    return HttpResponse.json({ environment: 'demo', testAccount: true })
  }),

  http.post('*/api/auth/smart-id/notification', async ({ request }) => {
    await delay()
    const body = (await request.json()) as {
      country?: string
      personalCode?: string
    }
    const code = body.personalCode?.trim() ?? ''
    if (
      !['EE', 'LV', 'LT'].includes(body.country ?? '') ||
      !/^[\d-]{11,12}$/.test(code)
    ) {
      return error(400, 'Invalid personal code')
    }
    if (code === '39001010002') {
      return error(404, 'No Smart-ID account for this personal code')
    }
    smartIdLogin = { personalCode: code, polls: 0 }
    return HttpResponse.json({ verificationCode: '2086' })
  }),

  http.post('*/api/auth/smart-id/status', async () => {
    await delay()
    if (!smartIdLogin) return error(404, 'No Smart-ID login in progress')
    smartIdLogin.polls += 1
    if (smartIdLogin.polls < 2) return HttpResponse.json({ state: 'running' })
    const { personalCode } = smartIdLogin
    smartIdLogin = null
    if (personalCode === '30403039917') {
      return HttpResponse.json({ state: 'failed', reason: 'user_refused' })
    }
    me = MOCK_CUSTOMER
    return HttpResponse.json({ state: 'complete', me })
  }),

  http.post('*/api/auth/test-account', async () => {
    await delay()
    me = {
      type: 'customer',
      name: 'Test User',
      country: 'EE',
      personalCode: '0000000****',
    }
    return HttpResponse.json(me)
  }),

  http.post('*/api/products', async ({ request }) => {
    await delay()
    const denied = requireAdmin()
    if (denied) return denied
    const input = productInputSchema.safeParse(await request.json())
    if (!input.success) return error(400, 'Invalid product')
    if (isSlugTaken(input.data.slug))
      return error(409, 'Slug is already in use')
    const product = { ...input.data, id: crypto.randomUUID() }
    products.push(product)
    return HttpResponse.json(product, { status: 201 })
  }),

  http.put('*/api/products/:id', async ({ params, request }) => {
    await delay()
    const denied = requireAdmin()
    if (denied) return denied
    const id = String(params.id)
    if (!findProduct(id)) return error(404, 'Product not found')
    const input = productInputSchema.safeParse(await request.json())
    if (!input.success) return error(400, 'Invalid product')
    if (isSlugTaken(input.data.slug, id))
      return error(409, 'Slug is already in use')
    const product = { ...input.data, id }
    products = products.map((p) => (p.id === id ? product : p))
    return HttpResponse.json(product)
  }),

  http.delete('*/api/products/:id', async ({ params }) => {
    await delay()
    const denied = requireAdmin()
    if (denied) return denied
    const id = String(params.id)
    if (!findProduct(id)) return error(404, 'Product not found')
    products = products.filter((p) => p.id !== id)
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/shipping/methods', async ({ request }) => {
    await delay()
    const country = new URL(request.url).searchParams.get('country') ?? ''
    const subtotal = buildCart().subtotal.amount
    const price = money(subtotal >= FREE_SHIPPING_FROM ? 0 : SHIPPING_FEE)
    return HttpResponse.json(
      CARRIERS.filter((c) => c.countries.includes(country)).map((c) => ({
        carrierCode: c.carrierCode,
        name: c.name,
        price,
      })),
    )
  }),

  http.get('*/api/shipping/pickup-points', async ({ request }) => {
    await delay()
    const params = new URL(request.url).searchParams
    const carrier = params.get('carrier')
    if (!CARRIERS.some((c) => c.carrierCode === carrier)) {
      return error(400, 'Unknown delivery method')
    }
    return HttpResponse.json(
      pickupPoints
        .filter(
          (p) =>
            p.carrierCode === carrier &&
            p.countryCode === params.get('country'),
        )
        .sort(
          (a, b) =>
            a.city.localeCompare(b.city) || a.name.localeCompare(b.name),
        ),
    )
  }),

  http.get('*/api/cart', async () => {
    await delay()
    return HttpResponse.json(buildCart())
  }),

  http.post('*/api/cart/items', async ({ request }) => {
    await delay()
    const body = (await request.json()) as {
      productId?: unknown
      quantity?: unknown
    }
    const product =
      typeof body.productId === 'string'
        ? findProduct(body.productId)
        : undefined
    if (!product) return error(404, 'Product not found')
    if (!isPositiveInt(body.quantity)) return error(400, 'Invalid quantity')
    if (product.stock === 0) return error(409, 'Product is out of stock')

    const quantity = Math.min(
      (cart.get(product.id) ?? 0) + body.quantity,
      product.stock,
      MAX_QUANTITY_PER_ITEM,
    )
    cart.set(product.id, quantity)
    return HttpResponse.json(buildCart())
  }),

  http.patch('*/api/cart/items/:productId', async ({ params, request }) => {
    await delay()
    const productId = String(params.productId)
    const body = (await request.json()) as { quantity?: unknown }
    if (!cart.has(productId)) return error(404, 'Item not in cart')
    if (
      !isPositiveInt(body.quantity) ||
      body.quantity > MAX_QUANTITY_PER_ITEM
    ) {
      return error(400, 'Invalid quantity')
    }
    cart.set(productId, body.quantity)
    return HttpResponse.json(buildCart())
  }),

  http.delete('*/api/cart/items/:productId', async ({ params }) => {
    await delay()
    cart.delete(String(params.productId))
    return HttpResponse.json(buildCart())
  }),

  http.post('*/api/orders', async ({ request }) => {
    await delay()
    const key = request.headers.get('Idempotency-Key')
    if (!key) return error(400, 'Missing Idempotency-Key header')

    // Same key again (e.g. a retried request): return the existing order.
    const existingId = idempotencyKeys.get(key)
    const existing = existingId ? orders.get(existingId) : undefined
    if (existing) return HttpResponse.json(withPaymentStatus(existing))

    const body = (await request.json()) as {
      email?: unknown
      country?: unknown
      carrierCode?: unknown
      pickupPointId?: unknown
    }
    if (typeof body.email !== 'string') return error(400, 'Invalid email')
    const pickupPoint = pickupPoints.find(
      (p) =>
        p.id === body.pickupPointId &&
        p.carrierCode === body.carrierCode &&
        p.countryCode === body.country,
    )
    if (!pickupPoint) return error(400, 'Unknown parcel machine')

    const current = buildCart()
    if (current.items.length === 0) return error(400, 'Cart is empty')
    if (current.items.some((i) => i.issue && i.issue !== 'price_changed')) {
      return error(409, 'Some items are no longer available')
    }

    // Totals are always recalculated here, never taken from the client.
    const subtotal = current.subtotal.amount
    const shipping = subtotal >= FREE_SHIPPING_FROM ? 0 : SHIPPING_FEE
    const order: StoredOrder = {
      id: `WS-${Math.floor(100000 + Math.random() * 900000)}`,
      status: 'pending_payment',
      email: body.email,
      total: money(subtotal + shipping),
      shipping: money(shipping),
      delivery: {
        carrierCode: pickupPoint.carrierCode,
        pickupPointName: pickupPoint.name,
        address: pickupPoint.address,
        city: pickupPoint.city,
      },
      // A real backend returns the payment provider's hosted checkout URL here.
      paymentUrl: null,
      createdAt: Date.now(),
    }
    for (const item of current.items) {
      const product = findProduct(item.productId)
      if (product) product.stock -= item.quantity
    }
    orders.set(order.id, order)
    idempotencyKeys.set(key, order.id)
    cart.clear()

    return HttpResponse.json(withPaymentStatus(order), { status: 201 })
  }),

  http.get('*/api/orders/:id', async ({ params }) => {
    await delay()
    const order = orders.get(String(params.id))
    return order
      ? HttpResponse.json(withPaymentStatus(order))
      : error(404, 'Order not found')
  }),
]
