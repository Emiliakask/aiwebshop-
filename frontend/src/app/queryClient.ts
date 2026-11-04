import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/apiClient'

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // Client errors (404, 401, …) won't fix themselves; only retry the rest.
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status < 500) &&
          failureCount < 2,
      },
    },
  })
}
