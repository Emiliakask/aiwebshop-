import { z } from 'zod'
import { moneySchema } from '@/lib/money'

/** A delivery option at checkout, priced by the backend for the current cart. */
export const shippingMethodSchema = z.object({
  carrierCode: z.string(),
  name: z.string(),
  price: moneySchema,
})

export type ShippingMethod = z.infer<typeof shippingMethodSchema>

/** A parcel machine (pakiautomaat). */
export const pickupPointSchema = z.object({
  id: z.string(),
  carrierCode: z.string(),
  name: z.string(),
  address: z.string(),
  city: z.string(),
  countryCode: z.string(),
  latitude: z.number().nullable(),
  longitude: z.number().nullable(),
})

export type PickupPoint = z.infer<typeof pickupPointSchema>

/** Case- and accent-insensitive match on name, address and city. */
export function matchesSearch(point: PickupPoint, search: string): boolean {
  const words = normalize(search).split(/\s+/).filter(Boolean)
  const haystack = normalize(`${point.name} ${point.address} ${point.city}`)
  return words.every((word) => haystack.includes(word))
}

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
}
