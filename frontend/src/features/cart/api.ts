import {
  useIsMutating,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { apiRequest } from '@/lib/apiClient'
import { cartSchema, type Cart } from './types'

export const cartKeys = {
  cart: ['cart'] as const,
}

/** Shared mutation key so the UI can tell when any cart change is in flight. */
const cartMutationKey = ['cart', 'mutation'] as const

export function useCart() {
  return useQuery({
    queryKey: cartKeys.cart,
    queryFn: ({ signal }) => apiRequest('/cart', cartSchema, { signal }),
  })
}

export function useIsCartUpdating() {
  return useIsMutating({ mutationKey: cartMutationKey }) > 0
}

type CartItemInput = { productId: string; quantity: number }

export function useAddToCart() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationKey: cartMutationKey,
    // Only the product and quantity are sent. The backend decides the price.
    mutationFn: (input: CartItemInput) =>
      apiRequest('/cart/items', cartSchema, { method: 'POST', body: input }),
    onSuccess: (cart) => queryClient.setQueryData(cartKeys.cart, cart),
  })
}

export function useUpdateCartItem() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationKey: cartMutationKey,
    mutationFn: ({ productId, quantity }: CartItemInput) =>
      apiRequest(`/cart/items/${encodeURIComponent(productId)}`, cartSchema, {
        method: 'PATCH',
        body: { quantity },
      }),
    onSuccess: (cart) => queryClient.setQueryData(cartKeys.cart, cart),
    onError: () => queryClient.invalidateQueries({ queryKey: cartKeys.cart }),
  })
}

export function useRemoveCartItem() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationKey: cartMutationKey,
    mutationFn: (productId: string) =>
      apiRequest(`/cart/items/${encodeURIComponent(productId)}`, cartSchema, {
        method: 'DELETE',
      }),
    // Optimistically hide the line; totals stay as they were until the
    // backend responds (the summary shows "Updating…" meanwhile).
    onMutate: async (productId) => {
      await queryClient.cancelQueries({ queryKey: cartKeys.cart })
      const previous = queryClient.getQueryData<Cart>(cartKeys.cart)
      if (previous) {
        const removed = previous.items.find((i) => i.productId === productId)
        queryClient.setQueryData<Cart>(cartKeys.cart, {
          ...previous,
          items: previous.items.filter((i) => i.productId !== productId),
          itemCount: previous.itemCount - (removed?.quantity ?? 0),
        })
      }
      return { previous }
    },
    onError: (_error, _productId, context) => {
      if (context?.previous) {
        queryClient.setQueryData(cartKeys.cart, context.previous)
      }
    },
    onSuccess: (cart) => queryClient.setQueryData(cartKeys.cart, cart),
  })
}
