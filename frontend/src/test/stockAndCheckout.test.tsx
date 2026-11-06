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
    await user.type(screen.getByLabelText('Mobile phone'), '+372 5123 4567')
    await user.selectOptions(screen.getByLabelText('Country'), 'EE')
    await user.selectOptions(
      await screen.findByLabelText('Parcel machine'),
      '96003',
    )
  }

  async function goToCheckout() {
    const app = renderApp('/products/stoneware-mug')
    await app.user.click(
      await screen.findByRole('button', { name: 'Add to cart' }),
    )
    await screen.findByText('Added to your cart.')
    await app.user.click(screen.getByRole('link', { name: 'Cart, 1 item' }))
    await app.user.click(
      await screen.findByRole('link', { name: 'Go to checkout' }),
    )
    return app
  }

  it('searches parcel machines and shows the chosen one', async () => {
    const { user } = await goToCheckout()
    await screen.findByRole('heading', { name: 'Checkout' })
    await user.selectOptions(screen.getByLabelText('Country'), 'EE')
    const picker = await screen.findByLabelText('Parcel machine')
    expect(screen.getByText('3 parcel machines')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Search parcel machines'), 'tartu')

    expect(screen.getByText('1 parcel machine')).toBeInTheDocument()
    expect(within(picker).getAllByRole('option')).toHaveLength(2) // placeholder + Märja
    await user.selectOptions(picker, '96338')
    expect(
      screen.getByText(
        'Selected: Märja Coop Konsumi pakiautomaat (Aisa tn 2, Tartu linn)',
      ),
    ).toBeInTheDocument()
  })

  it('shows Latvian machines when Latvia is chosen', async () => {
    const { user } = await goToCheckout()
    await screen.findByRole('heading', { name: 'Checkout' })
    await user.selectOptions(screen.getByLabelText('Country'), 'LV')
    const picker = await screen.findByLabelText('Parcel machine')
    expect(
      within(picker).getByRole('option', {
        name: 'Rīgas A. Briāna ielas RIMI pakomāts, Krišjāņa Valdemāra iela 62A',
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('1 parcel machine')).toBeInTheDocument()
  })

  it('requires a parcel machine and a valid phone before sending anything', async () => {
    let ordered = false
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/api/orders')) {
        ordered = true
      }
    })
    const { user } = await goToCheckout()
    await screen.findByRole('heading', { name: 'Checkout' })
    await user.type(screen.getByLabelText('Mobile phone'), 'call me')
    await user.selectOptions(screen.getByLabelText('Country'), 'EE')
    await screen.findByLabelText('Parcel machine')
    await user.click(
      screen.getByRole('button', { name: 'Place order and pay' }),
    )

    expect(
      await screen.findByText('Choose a parcel machine.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Enter a mobile number, e.g. +372 5123 4567.'),
    ).toBeInTheDocument()
    server.events.removeAllListeners()
    expect(ordered).toBe(false)
  })

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
    // No prices: the backend decides the product and delivery prices.
    expect(sent?.body).toEqual({
      email: 'ada@example.com',
      fullName: 'Ada Lovelace',
      phone: '+372 5123 4567',
      country: 'EE',
      carrierCode: 'omniva',
      pickupPointId: '96003',
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
