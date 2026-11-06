import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { renderApp } from './renderApp'

describe('shopping flow', () => {
  it('lists products and filters them by search', async () => {
    const { user } = renderApp('/')

    expect(
      await screen.findByRole('link', { name: 'Stoneware Mug' }),
    ).toBeInTheDocument()

    await user.type(
      screen.getByRole('searchbox', { name: 'Search products' }),
      'notebook',
    )
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByText('2 products')).toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: 'Stoneware Mug' }),
    ).not.toBeInTheDocument()
  })

  it('shows an error with a retry button when products fail to load', async () => {
    server.use(
      http.get('*/api/products', () =>
        HttpResponse.json({ message: 'Boom' }, { status: 500 }),
      ),
    )
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We couldn’t load the products.',
    )
    expect(
      screen.getByRole('button', { name: 'Try again' }),
    ).toBeInTheDocument()
  })

  it('adds a product to the cart and checks out', async () => {
    const { user, router } = renderApp('/products/stoneware-mug')

    await screen.findByRole('heading', { name: 'Stoneware Mug', level: 1 })
    await user.selectOptions(screen.getByLabelText('Quantity'), '2')
    await user.click(screen.getByRole('button', { name: 'Add to cart' }))

    expect(await screen.findByText('Added to your cart.')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Cart, 2 items' }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'View cart' }))
    const summary = await screen.findByRole('region', { name: 'Order summary' })
    expect(within(summary).getByText('€36.00')).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Go to checkout' }))
    await screen.findByRole('heading', { name: 'Checkout' })

    // Submitting empty shows validation errors and doesn't place an order.
    await user.click(
      screen.getByRole('button', { name: 'Place order and pay' }),
    )
    expect(
      await screen.findByText('Enter a valid email address.'),
    ).toBeInTheDocument()

    await user.type(screen.getByLabelText('Email'), 'ada@example.com')
    await user.type(screen.getByLabelText('Full name'), 'Ada Lovelace')
    await user.type(screen.getByLabelText('Mobile phone'), '+372 5123 4567')
    await user.selectOptions(screen.getByLabelText('Country'), 'EE')
    // Only one carrier, so it is chosen for us; then pick a parcel machine.
    expect(
      await screen.findByRole('radio', {
        name: 'Omniva parcel machine — €4.99',
      }),
    ).toBeChecked()
    await user.selectOptions(
      await screen.findByLabelText('Parcel machine'),
      '96003',
    )
    await user.click(
      screen.getByRole('button', { name: 'Place order and pay' }),
    )

    expect(
      await screen.findByRole('heading', { name: 'Confirming your payment…' }),
    ).toBeInTheDocument()
    expect(router.state.location.pathname).toMatch(/^\/orders\/WS-\d+$/)
    // €36.00 subtotal + €4.99 shipping, calculated by the backend.
    expect(screen.getByText('€40.99')).toBeInTheDocument()
    expect(
      screen.getByText('Tallinna Akadeemia Coop Konsumi pakiautomaat'),
    ).toBeInTheDocument()
  })

  it('removes an item and rolls back if the request fails', async () => {
    const { user } = renderApp('/products/linen-notebook')
    await user.click(await screen.findByRole('button', { name: 'Add to cart' }))
    await screen.findByText('Added to your cart.')
    await user.click(screen.getByRole('link', { name: 'View cart' }))

    server.use(
      http.delete('*/api/cart/items/:productId', () =>
        HttpResponse.json({ message: 'Nope' }, { status: 500 }),
      ),
    )
    await user.click(
      await screen.findByRole('button', {
        name: 'Remove Linen Notebook from cart',
      }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We couldn’t update your cart.',
    )
    expect(
      screen.getByRole('link', { name: 'Linen Notebook' }),
    ).toBeInTheDocument()
  })

  it('shows a not-found message for unknown products', async () => {
    renderApp('/products/does-not-exist')
    expect(
      await screen.findByRole('heading', { name: 'Product not found' }),
    ).toBeInTheDocument()
  })
})
