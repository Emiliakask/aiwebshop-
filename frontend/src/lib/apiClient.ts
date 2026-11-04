import type { z } from 'zod'

export const API_URL = import.meta.env.VITE_API_URL ?? '/api'

/** The backend sets this cookie; we echo it in a header to prove the request comes from our app. */
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const match = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(prefix))
  return match ? decodeURIComponent(match.slice(prefix.length)) : undefined
}

export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, message: string, body?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
}

/**
 * The only place that talks to the backend. Sends cookies (auth + guest cart
 * session) and the CSRF token, encodes JSON, turns non-2xx responses into
 * ApiError and validates the response body against the given Zod schema.
 */
export async function apiRequest<T>(
  path: string,
  schema: z.ZodType<T>,
  { method = 'GET', body, headers, signal }: RequestOptions = {},
): Promise<T> {
  const url = new URL(`${API_URL}${path}`, window.location.origin)
  const csrfToken = method === 'GET' ? undefined : readCookie(CSRF_COOKIE)

  const response = await fetch(url, {
    method,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...(csrfToken && { [CSRF_HEADER]: csrfToken }),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal,
  })

  const data: unknown = await response.json().catch(() => undefined)

  if (!response.ok) {
    throw new ApiError(response.status, getErrorMessage(data, response), data)
  }

  return schema.parse(data)
}

function getErrorMessage(data: unknown, response: Response): string {
  if (
    typeof data === 'object' &&
    data !== null &&
    'message' in data &&
    typeof data.message === 'string'
  ) {
    return data.message
  }
  return `Request failed with status ${response.status}`
}
