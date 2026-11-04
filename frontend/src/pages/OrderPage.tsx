import { useParams } from 'react-router'
import { ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PriceTag } from '@/components/ui/PriceTag'
import { Skeleton } from '@/components/ui/Skeleton'
import { useOrder } from '@/features/checkout/api'
import type { Order } from '@/features/checkout/types'
import { usePageMeta } from '@/hooks/usePageMeta'
import { strings } from '@/lib/i18n/strings'

const t = strings.order

function statusText(order: Order): { title: string; body: string } {
  switch (order.status) {
    case 'paid':
      return { title: t.paidTitle, body: t.paidBody(order.email) }
    case 'pending_payment':
      return { title: t.pendingTitle, body: t.pendingBody }
    case 'payment_failed':
      return { title: t.failedTitle, body: t.failedBody }
    case 'cancelled':
      return { title: t.cancelledTitle, body: t.cancelledBody }
  }
}

/**
 * Landing page after payment. The status always comes from the backend —
 * reaching this URL does not mean the order was paid.
 */
export function OrderPage() {
  const { orderId = '' } = useParams()
  const order = useOrder(orderId)
  const text = order.data ? statusText(order.data) : undefined
  usePageMeta(text?.title)

  if (order.isPending) {
    return (
      <div
        role="status"
        aria-label={strings.common.loading}
        className="mx-auto flex max-w-xl flex-col gap-4"
      >
        <Skeleton className="h-9 w-3/4" />
        <Skeleton className="h-20" />
      </div>
    )
  }

  if (order.isError || !text) {
    return <ErrorState message={t.loadError} onRetry={() => order.refetch()} />
  }

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-6 text-center">
      <div aria-live="polite" className="flex flex-col gap-3">
        <h1 className="text-3xl font-semibold tracking-tight">{text.title}</h1>
        <p className="text-stone-600">{text.body}</p>
      </div>
      <dl className="flex flex-col gap-2 rounded-2xl bg-stone-100 p-6 text-left">
        <div className="flex justify-between gap-4">
          <dt className="text-stone-600">{t.orderNumber}</dt>
          <dd className="font-mono text-sm">{order.data.id}</dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="text-stone-600">{t.total}</dt>
          <dd>
            <PriceTag price={order.data.total} className="font-semibold" />
          </dd>
        </div>
      </dl>
      <ButtonLink to="/" variant="secondary" className="self-center">
        {strings.common.continueShopping}
      </ButtonLink>
    </div>
  )
}
