import { useNavigate } from 'react-router'
import { ButtonLink } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useCart } from '@/features/cart/api'
import { CartSummary } from '@/features/cart/components/CartSummary'
import { hasBlockingIssue } from '@/features/cart/types'
import { CheckoutForm } from '@/features/checkout/components/CheckoutForm'
import type { Order } from '@/features/checkout/types'
import { usePageMeta } from '@/hooks/usePageMeta'
import { strings } from '@/lib/i18n/strings'

const t = strings.checkout

export function CheckoutPage() {
  usePageMeta(t.title)
  const cart = useCart()
  const navigate = useNavigate()

  function handleOrderCreated(order: Order) {
    if (order.paymentUrl && /^https?:\/\//.test(order.paymentUrl)) {
      // Payment happens on the provider's hosted page; it redirects back to
      // /orders/:id, which asks the backend whether the order was really paid.
      window.location.assign(order.paymentUrl)
    } else {
      navigate(`/orders/${order.id}`)
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <h1 className="text-3xl font-semibold tracking-tight">{t.title}</h1>

      {cart.isPending ? (
        <div
          role="status"
          aria-label={strings.common.loading}
          className="flex flex-col gap-4"
        >
          <Skeleton className="h-10" />
          <Skeleton className="h-64" />
        </div>
      ) : cart.isError ? (
        <ErrorState
          message={strings.cart.loadError}
          onRetry={() => cart.refetch()}
        />
      ) : cart.data.items.length === 0 ? (
        <EmptyState
          title={t.emptyCart}
          action={
            <ButtonLink to="/">{strings.common.continueShopping}</ButtonLink>
          }
        />
      ) : cart.data.items.some(hasBlockingIssue) ? (
        <EmptyState
          title={t.cartHasIssues}
          action={<ButtonLink to="/cart">{t.reviewCart}</ButtonLink>}
        />
      ) : (
        <div className="grid gap-10 lg:grid-cols-[1fr_22rem] lg:items-start">
          <CheckoutForm onOrderCreated={handleOrderCreated} />
          <CartSummary subtotal={cart.data.subtotal}>
            <ul className="flex flex-col gap-1 border-t border-stone-300 pt-4 text-sm text-stone-700">
              {cart.data.items.map((item) => (
                <li key={item.productId}>
                  {item.quantity} × {item.name}
                </li>
              ))}
            </ul>
            <ButtonLink to="/cart" variant="ghost" className="self-start">
              {t.reviewCart}
            </ButtonLink>
          </CartSummary>
        </div>
      )}
    </div>
  )
}
