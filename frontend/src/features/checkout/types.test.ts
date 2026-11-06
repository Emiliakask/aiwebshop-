import { describe, expect, it } from 'vitest'
import { checkoutFormSchema } from './types'

const valid = {
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  phone: '+372 5123 4567',
  country: 'EE',
  carrierCode: 'omniva',
  pickupPointId: '96003',
}

describe('checkoutFormSchema', () => {
  it('accepts contact details and a parcel machine', () => {
    expect(checkoutFormSchema.safeParse(valid).success).toBe(true)
  })

  it('trims whitespace', () => {
    const result = checkoutFormSchema.parse({
      ...valid,
      phone: '  5123 4567  ',
    })
    expect(result.phone).toBe('5123 4567')
  })

  it.each([
    ['email', 'not-an-email'],
    ['fullName', '   '],
    ['phone', 'call me'],
    ['phone', '123'],
    ['country', 'FI'],
    ['carrierCode', ''],
    ['pickupPointId', ''],
  ])('rejects an invalid %s (%s)', (field, value) => {
    const result = checkoutFormSchema.safeParse({ ...valid, [field]: value })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0]?.path).toEqual([field])
  })
})
