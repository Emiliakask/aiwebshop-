import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { renderApp } from './renderApp'

/** The mock answers "running" first, then the result, with the real 2 s polling interval. */
const POLL_WAIT = { timeout: 6000 }

async function enterCode(code: string) {
  const app = renderApp('/login?next=/cart')
  await app.user.type(
    await screen.findByRole('textbox', { name: 'Personal code' }),
    code,
  )
  await app.user.click(
    screen.getByRole('button', { name: 'Log in with Smart-ID' }),
  )
  return app
}

describe('customer login with Smart-ID', () => {
  it('shows the code to choose, then logs in and returns', async () => {
    const { router } = await enterCode('40504040001')

    expect(
      await screen.findByLabelText('Verification code 2 0 8 6'),
    ).toHaveTextContent('2086')
    expect(screen.getByText(/choose this code/)).toBeInTheDocument()

    await waitFor(
      () => expect(router.state.location.pathname).toBe('/cart'),
      POLL_WAIT,
    )
    expect(
      screen.getByRole('button', { name: 'Log out OK TEST' }),
    ).toBeInTheDocument()
  }, 10000)

  it('explains when the login is refused in the app', async () => {
    await enterCode('30403039917')

    expect(await screen.findByRole('alert', {}, POLL_WAIT)).toHaveTextContent(
      'cancelled in the Smart-ID app',
    )
    expect(
      screen.getByRole('button', { name: 'Log in with Smart-ID' }),
    ).toBeInTheDocument()
  }, 10000)

  it('checks the personal code before asking Smart-ID', async () => {
    let started = false
    server.use(
      http.post('*/api/auth/smart-id/notification', () => {
        started = true
        return HttpResponse.json({ verificationCode: '0000' })
      }),
    )
    await enterCode('123')

    expect(
      await screen.findByText('Enter a valid personal code.'),
    ).toBeInTheDocument()
    expect(started).toBe(false)
  })

  it('says when the person has no Smart-ID account', async () => {
    await enterCode('39001010002')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'no Smart-ID account',
    )
  })

  it('offers the demo test account', async () => {
    const { user, router } = renderApp('/login')

    await user.click(
      await screen.findByRole('button', { name: 'Log in as test user' }),
    )

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(
      await screen.findByRole('button', { name: 'Log out Test User' }),
    ).toBeInTheDocument()
  })

  it('hides the test account when the backend says so (production)', async () => {
    server.use(
      http.get('*/api/auth/smart-id/config', () =>
        HttpResponse.json({ environment: 'production', testAccount: false }),
      ),
    )
    renderApp('/login')

    await screen.findByRole('button', { name: 'Log in with Smart-ID' })
    expect(
      screen.queryByRole('button', { name: 'Log in as test user' }),
    ).not.toBeInTheDocument()
  })

  it('a logged-in customer does not get the admin module', async () => {
    server.use(
      http.get('*/api/me', () =>
        HttpResponse.json({
          type: 'customer',
          name: 'OK TEST',
          country: 'EE',
          personalCode: '4050404****',
        }),
      ),
    )
    renderApp('/admin')

    expect(
      await screen.findByRole('link', { name: 'Log in with Google' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Add product' }),
    ).not.toBeInTheDocument()
  })
})
