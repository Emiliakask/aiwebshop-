import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiRequest } from '@/lib/apiClient'
import {
  productListSchema,
  productSchema,
  type ProductListParams,
} from './types'

export const productKeys = {
  all: ['products'] as const,
  list: (params: ProductListParams) =>
    [...productKeys.all, 'list', params] as const,
  detail: (slug: string) => [...productKeys.all, 'detail', slug] as const,
}

export function useProducts(params: ProductListParams) {
  return useQuery({
    queryKey: productKeys.list(params),
    queryFn: ({ signal }) => {
      const query = new URLSearchParams({ sort: params.sort })
      if (params.search) query.set('search', params.search)
      return apiRequest(`/products?${query}`, productListSchema, { signal })
    },
    placeholderData: keepPreviousData,
  })
}

export function useProduct(slug: string) {
  return useQuery({
    queryKey: productKeys.detail(slug),
    queryFn: ({ signal }) =>
      apiRequest(`/products/${encodeURIComponent(slug)}`, productSchema, {
        signal,
      }),
  })
}
