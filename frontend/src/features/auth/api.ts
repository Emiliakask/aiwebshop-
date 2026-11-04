import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { API_URL, ApiError, apiRequest } from '@/lib/apiClient'
import { meSchema } from './types'

export const authKeys = {
  me: ['auth', 'me'] as const,
}

/** Backend URL that starts the Google login for admins. */
export const ADMIN_LOGIN_URL = `${API_URL.replace(/\/api\/?$/, '')}/oauth2/authorization/google`

/** The logged-in user, or null when nobody is logged in (401). */
export function useMe() {
  return useQuery({
    queryKey: authKeys.me,
    queryFn: async ({ signal }) => {
      try {
        return await apiRequest('/me', meSchema, { signal })
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiRequest('/logout', z.undefined(), { method: 'POST' }),
    onSuccess: () => queryClient.setQueryData(authKeys.me, null),
  })
}
