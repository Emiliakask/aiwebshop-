import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cartKeys } from '@/features/cart/api'
import { apiRequest } from '@/lib/apiClient'
import { orderSchema, type CheckoutFormValues } from './types'

export const orderKeys = {
  all: ['orders'] as const,
  detail: (id: string) => [...orderKeys.all, 'detail', id] as const,
}

/** How often to re-check an order while the payment is still being confirmed. */
const PENDING_POLL_INTERVAL_MS = 2000

type CreateOrderInput = {
  values: CheckoutFormValues
  /** Same key for retries of the same checkout, so the backend never creates two orders. */
  idempotencyKey: string
}

export function useCreateOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    // The order is built from the server-side cart; only contact and shipping details are sent.
    mutationFn: ({ values, idempotencyKey }: CreateOrderInput) =>
      apiRequest('/orders', orderSchema, {
        method: 'POST',
        body: values,
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    onSuccess: (order) => {
      queryClient.setQueryData(orderKeys.detail(order.id), order)
      return queryClient.invalidateQueries({ queryKey: cartKeys.cart })
    },
    // E.g. 409 when an item sold out meanwhile: reload the cart so its issues show.
    onError: () => queryClient.invalidateQueries({ queryKey: cartKeys.cart }),
  })
}

export function useOrder(id: string) {
  return useQuery({
    queryKey: orderKeys.detail(id),
    queryFn: ({ signal }) =>
      apiRequest(`/orders/${encodeURIComponent(id)}`, orderSchema, { signal }),
    refetchInterval: (query) =>
      query.state.data?.status === 'pending_payment'
        ? PENDING_POLL_INTERVAL_MS
        : false,
  })
}
