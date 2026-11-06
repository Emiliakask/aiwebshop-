import { z } from 'zod'

/** Who is logged in (docs/auth.md). Admins use Google, customers use Smart-ID. */
export const adminSchema = z.object({
  type: z.literal('admin'),
  name: z.string(),
  email: z.string(),
})

export const customerSchema = z.object({
  type: z.literal('customer'),
  name: z.string(),
  country: z.string(),
  /** Always masked by the backend, e.g. "4050404****". */
  personalCode: z.string(),
})

export const meSchema = z.discriminatedUnion('type', [
  adminSchema,
  customerSchema,
])

export type Me = z.infer<typeof meSchema>
export type Admin = z.infer<typeof adminSchema>
export type Customer = z.infer<typeof customerSchema>

export const smartIdCountries = ['EE', 'LV', 'LT'] as const
export type SmartIdCountry = (typeof smartIdCountries)[number]

export const smartIdConfigSchema = z.object({
  environment: z.enum(['demo', 'production']),
  testAccount: z.boolean(),
})

export const chooseNumberStartedSchema = z.object({
  verificationCode: z.string().regex(/^\d{4}$/),
})

export const loginFailureReasons = [
  'user_refused',
  'timeout',
  'wrong_vc',
  'error',
] as const

export const loginStatusSchema = z.discriminatedUnion('state', [
  z.object({ state: z.literal('running') }),
  z.object({ state: z.literal('complete'), me: customerSchema }),
  z.object({ state: z.literal('failed'), reason: z.enum(loginFailureReasons) }),
])

export type LoginStatus = z.infer<typeof loginStatusSchema>

/** Same rules as the backend: EE/LT 11 digits, LV 123456-12345 (dash optional). */
export function isValidPersonalCode(country: SmartIdCountry, code: string) {
  const value = code.trim()
  return country === 'LV'
    ? /^\d{6}-?\d{5}$/.test(value)
    : /^[1-6]\d{10}$/.test(value)
}
