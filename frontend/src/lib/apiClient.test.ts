import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { z } from 'zod'
import { server } from '@/mocks/server'
import { apiRequest } from './apiClient'

function captureHeaders() {
  const seen: Headers[] = []
  server.use(
    http.all('*/api/echo', ({ request }) => {
      seen.push(request.headers)
      return HttpResponse.json({})
    }),
  )
  return seen
}

describe('apiRequest CSRF protection', () => {
  afterEach(() => {
    document.cookie =
      'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('sends the XSRF-TOKEN cookie back as a header on changes', async () => {
    document.cookie = 'XSRF-TOKEN=abc%20123; path=/'
    const seen = captureHeaders()

    await apiRequest('/echo', z.object({}), { method: 'POST', body: {} })

    expect(seen.at(0)?.get('X-XSRF-TOKEN')).toBe('abc 123')
  })

  it('does not send it on reads', async () => {
    document.cookie = 'XSRF-TOKEN=abc; path=/'
    const seen = captureHeaders()

    await apiRequest('/echo', z.object({}))

    expect(seen.at(0)?.has('X-XSRF-TOKEN')).toBe(false)
  })
})
