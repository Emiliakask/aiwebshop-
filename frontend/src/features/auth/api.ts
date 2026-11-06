import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { z } from 'zod'
import { API_URL, ApiError, apiRequest } from '@/lib/apiClient'
import {
  chooseNumberStartedSchema,
  customerSchema,
  loginStatusSchema,
  meSchema,
  smartIdConfigSchema,
  type SmartIdCountry,
} from './types'

export const authKeys = {
  me: ['auth', 'me'] as const,
  smartIdConfig: ['auth', 'smart-id', 'config'] as const,
}

/** How often the page asks whether the Smart-ID app has answered. */
export const LOGIN_POLL_MS = 2000

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

export function useSmartIdConfig() {
  return useQuery({
    queryKey: authKeys.smartIdConfig,
    queryFn: ({ signal }) =>
      apiRequest('/auth/smart-id/config', smartIdConfigSchema, { signal }),
    staleTime: Infinity,
  })
}

/** Starts "choose number": the backend answers with the code to show. */
export function useStartChooseNumber() {
  return useMutation({
    mutationFn: (input: { country: SmartIdCountry; personalCode: string }) =>
      apiRequest('/auth/smart-id/notification', chooseNumberStartedSchema, {
        method: 'POST',
        body: input,
      }),
  })
}

/** One status check. POST because a completed check logs the browser in. */
export function pollLoginStatus() {
  return apiRequest('/auth/smart-id/status', loginStatusSchema, {
    method: 'POST',
  })
}

export function useTestAccountLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiRequest('/auth/test-account', customerSchema, { method: 'POST' }),
    onSuccess: (me) => queryClient.setQueryData(authKeys.me, me),
  })
}
