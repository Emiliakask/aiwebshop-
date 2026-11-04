import { z } from 'zod'

/** The shop sells in euros only. */
export const SHOP_CURRENCY = 'EUR'

/** Prices are always whole euro cents, e.g. { amount: 1999, currency: 'EUR' } = €19.99. */
export const moneySchema = z.object({
  amount: z.number().int(),
  currency: z.literal(SHOP_CURRENCY),
})

export type Money = z.infer<typeof moneySchema>

export const APP_LOCALE = 'en-IE'

const formatters = new Map<string, Intl.NumberFormat>()

function getFormatter(currency: string, locale: string): Intl.NumberFormat {
  const key = `${locale}:${currency}`
  let formatter = formatters.get(key)
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, { style: 'currency', currency })
    formatters.set(key, formatter)
  }
  return formatter
}

/** The only way prices should be turned into text. */
export function formatMoney(money: Money, locale: string = APP_LOCALE): string {
  const formatter = getFormatter(money.currency, locale)
  // Division happens only here, for display. Never do arithmetic on the result.
  const fractionDigits = formatter.resolvedOptions().maximumFractionDigits ?? 2
  return formatter.format(money.amount / 10 ** fractionDigits)
}
