import { z } from 'zod'
import type { Product } from '@/features/products/types'
import { strings } from '@/lib/i18n/strings'
import { SHOP_CURRENCY } from '@/lib/money'

const t = strings.admin.errors

const requiredText = z.string().trim().min(1, t.required).max(200, t.tooLong)

export const productFormSchema = z.object({
  name: requiredText,
  slug: z
    .string()
    .trim()
    .regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, t.slug),
  category: requiredText,
  description: z.string().trim().max(5000, t.tooLong),
  /** Typed as text like "12.99" and turned into cents without floating point. */
  price: z
    .string()
    .trim()
    .regex(/^\d{1,7}([.,]\d{1,2})?$/, t.price),
  stock: z.number(t.stock).int(t.stock).min(0, t.stock),
  imageUrl: z.string().trim().min(1, t.required),
  imageAlt: requiredText,
})

export type ProductFormValues = z.infer<typeof productFormSchema>

/** What the backend accepts for create and update. */
export type ProductInput = Omit<Product, 'id'>

/** "12.99" / "12,9" / "12" -> cents, using integer maths only. */
export function toCents(price: string): number {
  const [whole, fraction = ''] = price.trim().split(/[.,]/)
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
}

/** 1299 -> "12.99", for pre-filling the price field. */
export function fromCents(amount: number): string {
  const cents = String(amount % 100).padStart(2, '0')
  return `${Math.floor(amount / 100)}.${cents}`
}

/** Prices are always euros. */
export function toProductInput(values: ProductFormValues): ProductInput {
  return {
    ...values,
    price: { amount: toCents(values.price), currency: SHOP_CURRENCY },
  }
}

export function toFormValues(product?: Product): ProductFormValues {
  if (!product) {
    return {
      name: '',
      slug: '',
      category: '',
      description: '',
      price: '',
      stock: 0,
      imageUrl: '',
      imageAlt: '',
    }
  }
  return {
    name: product.name,
    slug: product.slug,
    category: product.category,
    description: product.description,
    price: fromCents(product.price.amount),
    stock: product.stock,
    imageUrl: product.imageUrl,
    imageAlt: product.imageAlt,
  }
}
