import { describe, expect, it } from 'vitest'
import { formatMoney, moneySchema } from './money'

describe('formatMoney', () => {
  it('formats cents as a currency amount', () => {
    expect(formatMoney({ amount: 1999, currency: 'EUR' }, 'en-IE')).toBe(
      '€19.99',
    )
  })

  it('formats zero and whole amounts with two decimals', () => {
    expect(formatMoney({ amount: 0, currency: 'EUR' }, 'en-IE')).toBe('€0.00')
    expect(formatMoney({ amount: 500, currency: 'EUR' }, 'en-IE')).toBe('€5.00')
  })

  it('uses the locale’s separators', () => {
    expect(formatMoney({ amount: 123456, currency: 'EUR' }, 'de-DE')).toBe(
      '1.234,56 €',
    )
  })

  it('shows cents exactly, without floating-point noise', () => {
    // 0.1 + 0.2 = 0.30000000000000004 in floating point; in cents it is 10 + 20 = 30.
    expect(formatMoney({ amount: 10 + 20, currency: 'EUR' }, 'en-IE')).toBe(
      '€0.30',
    )
    expect(formatMoney({ amount: 1999 * 3, currency: 'EUR' }, 'en-IE')).toBe(
      '€59.97',
    )
  })
})

describe('moneySchema', () => {
  it('accepts whole euro cents', () => {
    expect(
      moneySchema.safeParse({ amount: 1999, currency: 'EUR' }).success,
    ).toBe(true)
  })

  it('rejects other currencies', () => {
    expect(
      moneySchema.safeParse({ amount: 1999, currency: 'USD' }).success,
    ).toBe(false)
  })

  it('rejects fractional cents instead of rounding them', () => {
    expect(
      moneySchema.safeParse({ amount: 19.99, currency: 'EUR' }).success,
    ).toBe(false)
  })
})
