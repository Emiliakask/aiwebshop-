import { z } from 'zod'
import { moneySchema } from '@/lib/money'

export const productSchema = z.object({
  id: z.string(),
  slug: z.string(),
  name: z.string(),
  description: z.string(),
  category: z.string(),
  price: moneySchema,
  imageUrl: z.string(),
  imageAlt: z.string(),
  stock: z.number().int().nonnegative(),
})

export type Product = z.infer<typeof productSchema>

export const productListSchema = z.object({
  items: z.array(productSchema),
  total: z.number().int().nonnegative(),
})

export type ProductList = z.infer<typeof productListSchema>

export const productSorts = [
  'featured',
  'price-asc',
  'price-desc',
  'name',
] as const

export type ProductSort = (typeof productSorts)[number]

export type ProductListParams = {
  search: string
  sort: ProductSort
}

export function parseProductSort(value: string | null): ProductSort {
  return productSorts.find((sort) => sort === value) ?? 'featured'
}

/** Stock level at which we start showing "Only N left". */
export const LOW_STOCK_THRESHOLD = 5
