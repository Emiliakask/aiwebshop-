import type { ReactNode } from 'react'
import { PriceTag } from '@/components/ui/PriceTag'
import { strings } from '@/lib/i18n/strings'
import type { Money } from '@/lib/money'
import { useIsCartUpdating } from '../api'

type CartSummaryProps = {
  subtotal: Money
  children?: ReactNode
}

/** Subtotal is a preview from the backend; the final total is set at checkout. */
export function CartSummary({ subtotal, children }: CartSummaryProps) {
  const updating = useIsCartUpdating()
  return (
    <section
      aria-labelledby="cart-summary-heading"
      className="flex flex-col gap-4 rounded-2xl bg-stone-100 p-6"
    >
      <h2 id="cart-summary-heading" className="text-lg font-semibold">
        {strings.cart.summaryTitle}
      </h2>
      <div className="flex items-baseline justify-between">
        <span>{strings.cart.subtotal}</span>
        <span aria-live="polite" className="text-lg font-semibold">
          {updating ? (
            <span className="text-base font-normal text-stone-500">
              {strings.cart.updating}
            </span>
          ) : (
            <PriceTag price={subtotal} />
          )}
        </span>
      </div>
      <p className="text-sm text-stone-600">{strings.cart.taxNote}</p>
      {children}
    </section>
  )
}
