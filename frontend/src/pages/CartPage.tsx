import { useState } from 'react'
import { ButtonLink } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { StatusMessage } from '@/components/ui/StatusMessage'
import { useCart } from '@/features/cart/api'
import { CartLine } from '@/features/cart/components/CartLine'
import { CartSummary } from '@/features/cart/components/CartSummary'
import { hasBlockingIssue } from '@/features/cart/types'
import { usePageMeta } from '@/hooks/usePageMeta'
import { strings } from '@/lib/i18n/strings'

const t = strings.cart

export function CartPage() {
  usePageMeta(t.title)
  const cart = useCart()
  const [updateFailed, setUpdateFailed] = useState(false)

  return (
    <div className="flex flex-col gap-8">
      <h1 className="text-3xl font-semibold tracking-tight">{t.title}</h1>

      {cart.isPending ? (
        <div
          role="status"
          aria-label={strings.common.loading}
          className="flex flex-col gap-4"
        >
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      ) : cart.isError ? (
        <ErrorState message={t.loadError} onRetry={() => cart.refetch()} />
      ) : cart.data.items.length === 0 ? (
        <EmptyState
          title={t.emptyTitle}
          body={t.emptyBody}
          action={
            <ButtonLink to="/">{strings.common.continueShopping}</ButtonLink>
          }
        />
      ) : (
        <div className="grid gap-8 lg:grid-cols-[1fr_22rem] lg:items-start">
          <div className="flex flex-col gap-4">
            {updateFailed && (
              <StatusMessage tone="error">{t.updateError}</StatusMessage>
            )}
            <ul className="divide-y divide-stone-200 border-y border-stone-200">
              {cart.data.items.map((item) => (
                <CartLine
                  key={item.productId}
                  item={item}
                  onError={() => setUpdateFailed(true)}
                />
              ))}
            </ul>
          </div>

          <CartSummary subtotal={cart.data.subtotal}>
            {cart.data.items.some(hasBlockingIssue) ? (
              <StatusMessage tone="warning">{t.fixIssues}</StatusMessage>
            ) : (
              <ButtonLink to="/checkout" size="lg">
                {t.checkout}
              </ButtonLink>
            )}
          </CartSummary>
        </div>
      )}
    </div>
  )
}
