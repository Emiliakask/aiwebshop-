import { describe, expect, it } from 'vitest'
import { checkoutFormSchema } from './types'

const valid = {
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  addressLine1: 'Narva mnt 1',
  city: 'Tallinn',
  postalCode: '10117',
  country: 'EE',
}

describe('checkoutFormSchema', () => {
  it('accepts a complete address', () => {
    expect(checkoutFormSchema.safeParse(valid).success).toBe(true)
  })

  it('trims whitespace', () => {
    const result = checkoutFormSchema.parse({ ...valid, city: '  Tallinn  ' })
    expect(result.city).toBe('Tallinn')
  })

  it.each([
    ['email', 'not-an-email'],
    ['fullName', '   '],
    ['postalCode', '!'],
    ['country', 'XX'],
  ])('rejects an invalid %s', (field, value) => {
    const result = checkoutFormSchema.safeParse({ ...valid, [field]: value })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0]?.path).toEqual([field])
  })
})
