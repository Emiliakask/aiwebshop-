import { z } from 'zod'
import { moneySchema } from '@/lib/money'

/**
 * Problems the backend detected with a cart line since it was added:
 * the item sold out, there is less stock than requested, or the price changed.
 */
export const cartItemIssueSchema = z.enum([
  'out_of_stock',
  'insufficient_stock',
  'price_changed',
])

export type CartItemIssue = z.infer<typeof cartItemIssueSchema>

export const cartItemSchema = z.object({
  productId: z.string(),
  slug: z.string(),
  name: z.string(),
  imageUrl: z.string(),
  imageAlt: z.string(),
  unitPrice: moneySchema,
  quantity: z.number().int().positive(),
  lineTotal: moneySchema,
  stock: z.number().int().nonnegative(),
  issue: cartItemIssueSchema.nullable(),
})

export type CartItem = z.infer<typeof cartItemSchema>

export const cartSchema = z.object({
  items: z.array(cartItemSchema),
  itemCount: z.number().int().nonnegative(),
  subtotal: moneySchema,
})

export type Cart = z.infer<typeof cartSchema>

/** Issues that must be fixed before the user can check out. */
export function hasBlockingIssue(item: CartItem): boolean {
  return item.issue === 'out_of_stock' || item.issue === 'insufficient_stock'
}

/** Most items one cart line can hold, regardless of stock. */
export const MAX_QUANTITY_PER_ITEM = 10
