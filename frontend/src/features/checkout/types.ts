import { z } from 'zod'
import { strings } from '@/lib/i18n/strings'
import { moneySchema } from '@/lib/money'

const t = strings.checkout.errors

/** Countries with parcel machine delivery (Omniva: Estonia, Latvia, Lithuania). */
export const shippingCountries = ['EE', 'LV', 'LT'] as const

export type ShippingCountry = (typeof shippingCountries)[number]

const requiredText = z.string().trim().min(1, t.required).max(200, t.tooLong)

export const checkoutFormSchema = z.object({
  email: z.string().trim().pipe(z.email(t.email)),
  fullName: requiredText,
  /** The carrier texts the pickup code to this number. */
  phone: z
    .string()
    .trim()
    .regex(/^\+?[0-9][0-9 ]{6,19}$/, t.phone),
  country: z.enum(shippingCountries, t.required),
  carrierCode: z.string().min(1, t.carrier),
  pickupPointId: z.string().min(1, t.pickupPoint),
})

export type CheckoutFormValues = z.infer<typeof checkoutFormSchema>

export const orderStatusSchema = z.enum([
  'pending_payment',
  'paid',
  'payment_failed',
  'cancelled',
])

export type OrderStatus = z.infer<typeof orderStatusSchema>

export const orderSchema = z.object({
  id: z.string(),
  status: orderStatusSchema,
  email: z.string(),
  total: moneySchema,
  shipping: moneySchema,
  /** The parcel machine the order goes to (a snapshot taken at order time). Null on old orders. */
  delivery: z
    .object({
      carrierCode: z.string(),
      pickupPointName: z.string(),
      address: z.string(),
      city: z.string(),
    })
    .nullable(),
  /** Where to send the user to pay (payment provider's hosted page). */
  paymentUrl: z.string().nullable(),
})

export type Order = z.infer<typeof orderSchema>
