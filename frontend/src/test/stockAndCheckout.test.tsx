import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { renderApp } from './renderApp'

describe('out-of-stock products', () => {
  it('are shown as unavailable on the products page', async () => {
    renderApp('/')

    const link = await screen.findByRole('link', { name: 'Brass Pen' })
    const card = link.closest('article')!
    expect(card).toHaveAttribute('data-unavailable', 'true')
    expect(within(card).getByText('Unavailable')).toBeInTheDocument()

    const mug = screen
      .getByRole('link', { name: 'Stoneware Mug' })
      .closest('article')!
    expect(mug).not.toHaveAttribute('data-unavailable')
  })

  it('cannot be added to the cart', async () => {
    renderApp('/products/brass-pen')

    expect(
      await screen.findByRole('button', { name: 'Unavailable' }),
    ).toBeDisabled()
    expect(screen.queryByLabelText('Quantity')).not.toBeInTheDocument()
  })
})

describe('checkout', () => {
  async function fillCheckout(user: ReturnType<typeof renderApp>['user']) {
    await screen.findByRole('heading', { name: 'Checkout' })
    await user.type(screen.getByLabelText('Email'), 'ada@example.com')
    await user.type(screen.getByLabelText('Full name'), 'Ada Lovelace')
    await user.type(screen.getByLabelText('Street address'), 'Narva mnt 1')
    await user.type(screen.getByLabelText('City'), 'Tallinn')
    await user.type(screen.getByLabelText('Postal code'), '10117')
    await user.selectOptions(screen.getByLabelText('Country'), 'EE')
  }

  it('sends the order to the backend with only contact details and an idempotency key', async () => {
    let sent: { body: unknown; key: string | null } | undefined
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/api/orders')) {
        sent = {
          body: await request.clone().json(),
          key: request.headers.get('Idempotency-Key'),
        }
      }
    })
    const { user } = renderApp('/products/stoneware-mug')
    await user.click(await screen.findByRole('button', { name: 'Add to cart' }))
    await screen.findByText('Added to your cart.')
    await user.click(screen.getByRole('link', { name: 'Cart, 1 item' }))
    await user.click(
      await screen.findByRole('link', { name: 'Go to checkout' }),
    )
    await fillCheckout(user)
    await user.click(
      screen.getByRole('button', { name: 'Place order and pay' }),
    )

    await screen.findByRole('heading', { name: 'Confirming your payment…' })
    server.events.removeAllListeners()
    expect(sent?.key).toMatch(/^[0-9a-f-]{36}$/)
    expect(sent?.body).toEqual({
      email: 'ada@example.com',
      fullName: 'Ada Lovelace',
      addressLine1: 'Narva mnt 1',
      city: 'Tallinn',
      postalCode: '10117',
      country: 'EE',
    })
  })

  it('explains when the backend refuses because something sold out', async () => {
    server.use(
      http.post('*/api/orders', () =>
        HttpResponse.json(
          { message: 'Some items are no longer available' },
          { status: 409 },
        ),
      ),
    )
    const { user } = renderApp('/products/stoneware-mug')
    await user.click(await screen.findByRole('button', { name: 'Add to cart' }))
    await screen.findByText('Added to your cart.')
    await user.click(screen.getByRole('link', { name: 'Cart, 1 item' }))
    await user.click(
      await screen.findByRole('link', { name: 'Go to checkout' }),
    )
    await fillCheckout(user)
    await user.click(
      screen.getByRole('button', { name: 'Place order and pay' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Some items sold out while you were checking out.',
    )
  })
})
