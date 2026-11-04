import { useMutation, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { productKeys } from '@/features/products/api'
import { productSchema } from '@/features/products/types'
import { apiRequest } from '@/lib/apiClient'
import type { ProductInput } from './types'

type SaveProductInput = {
  /** Missing = create a new product. */
  id?: string
  input: ProductInput
}

export function useSaveProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: SaveProductInput) =>
      apiRequest(
        id ? `/products/${encodeURIComponent(id)}` : '/products',
        productSchema,
        { method: id ? 'PUT' : 'POST', body: input },
      ),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: productKeys.all }),
  })
}

export function useDeleteProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest(`/products/${encodeURIComponent(id)}`, z.undefined(), {
        method: 'DELETE',
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: productKeys.all }),
  })
}
