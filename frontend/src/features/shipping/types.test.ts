import { describe, expect, it } from 'vitest'
import { matchesSearch, type PickupPoint } from './types'

const point: PickupPoint = {
  id: '96003',
  carrierCode: 'omniva',
  name: 'Tallinna Akadeemia Coop Konsumi pakiautomaat',
  address: 'Akadeemia tee 35',
  city: 'Tallinn',
  countryCode: 'EE',
  latitude: null,
  longitude: null,
}

describe('matchesSearch', () => {
  it.each([
    ['', true],
    ['tallinn', true],
    ['AKADEEMIA 35', true],
    ['coop tallinn', true],
    ['tartu', false],
  ])('"%s" → %s', (search, expected) => {
    expect(matchesSearch(point, search)).toBe(expected)
  })

  it('ignores accents, so "riga" finds "Rīga"', () => {
    expect(
      matchesSearch(
        { ...point, city: 'Rīga', name: 'Pakomāts' },
        'riga pakomats',
      ),
    ).toBe(true)
  })
})
