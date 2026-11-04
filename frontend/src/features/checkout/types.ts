import { z } from 'zod'
import { strings } from '@/lib/i18n/strings'
import { moneySchema } from '@/lib/money'

const t = strings.checkout.errors

export const shippingCountries = ['EE', 'FI', 'LV', 'LT', 'SE', 'DE'] as const

export type ShippingCountry = (typeof shippingCountries)[number]

const requiredText = z.string().trim().min(1, t.required).max(200, t.tooLong)

export const checkoutFormSchema = z.object({
  email: z.string().trim().pipe(z.email(t.email)),
  fullName: requiredText,
  addressLine1: requiredText,
  city: requiredText,
  postalCode: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9 -]{3,10}$/, t.postalCode),
  country: z.enum(shippingCountries, t.required),
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
  /** Where to send the user to pay (payment provider's hosted page). */
  paymentUrl: z.string().nullable(),
})

export type Order = z.infer<typeof orderSchema>
