import { describe, expect, it } from 'vitest'
import { fromCents, productFormSchema, toCents, toFormValues } from './types'

describe('toCents', () => {
  it.each([
    ['12.99', 1299],
    ['12,9', 1290],
    ['12', 1200],
    ['0.05', 5],
    ['0.10', 10],
  ])('turns %s into %i cents', (input, cents) => {
    expect(toCents(input)).toBe(cents)
  })
})

describe('fromCents', () => {
  it.each([
    [1299, '12.99'],
    [5, '0.05'],
    [1200, '12.00'],
  ])('turns %i cents into %s', (cents, text) => {
    expect(fromCents(cents)).toBe(text)
  })
})

describe('productFormSchema', () => {
  const valid = {
    ...toFormValues(),
    name: 'Mug',
    slug: 'mug',
    category: 'Kitchen',
    price: '12.99',
    imageUrl: 'https://example.com/mug.jpg',
    imageAlt: 'White mug',
  }

  it('accepts a valid product', () => {
    expect(productFormSchema.safeParse(valid).success).toBe(true)
  })

  it.each(['12.999', '-1', 'abc', '1.2.3'])('rejects price %s', (price) => {
    expect(productFormSchema.safeParse({ ...valid, price }).success).toBe(false)
  })

  it.each(['Mug', 'my mug', '-mug', 'mug-'])('rejects slug %s', (slug) => {
    expect(productFormSchema.safeParse({ ...valid, slug }).success).toBe(false)
  })

  it('rejects negative or fractional stock', () => {
    expect(productFormSchema.safeParse({ ...valid, stock: -1 }).success).toBe(
      false,
    )
    expect(productFormSchema.safeParse({ ...valid, stock: 1.5 }).success).toBe(
      false,
    )
  })
})
