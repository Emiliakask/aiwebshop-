import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { renderApp } from './renderApp'

describe('admin login', () => {
  it('asks to log in with Google when nobody is logged in', async () => {
    server.use(
      http.get('*/api/me', () =>
        HttpResponse.json({ message: 'Not logged in' }, { status: 401 }),
      ),
    )
    renderApp('/admin')

    const login = await screen.findByRole('link', {
      name: 'Log in with Google',
    })
    expect(login).toHaveAttribute(
      'href',
      expect.stringMatching(/\/oauth2\/authorization\/google$/),
    )
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Add product' }),
    ).not.toBeInTheDocument()
  })

  it('explains when the Google account is not an admin', async () => {
    server.use(
      http.get('*/api/me', () =>
        HttpResponse.json({ message: 'Not logged in' }, { status: 401 }),
      ),
    )
    renderApp('/admin?loginError')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'isn’t allowed to manage this shop',
    )
  })

  it('shows who is logged in and logs out', async () => {
    const { user } = await openAdmin()
    expect(
      screen.getByText('Logged in as admin@example.com'),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Log out' }))

    expect(
      await screen.findByRole('link', { name: 'Log in with Google' }),
    ).toBeInTheDocument()
  })
})

async function openAdmin() {
  const app = renderApp('/admin')
  await screen.findByRole('table')
  return app
}

describe('admin products', () => {
  it('adds a product', async () => {
    const { user } = await openAdmin()

    await user.click(screen.getByRole('button', { name: 'Add product' }))
    const form = screen.getByRole('region', { name: 'New product' })
    await user.type(within(form).getByLabelText('Name'), 'Test Lamp')
    await user.type(within(form).getByLabelText('URL slug'), 'test-lamp')
    await user.type(within(form).getByLabelText('Category'), 'Lighting')
    await user.type(
      within(form).getByLabelText('Image URL'),
      'https://example.com/lamp.jpg',
    )
    await user.type(
      within(form).getByLabelText('Image description (alt text)'),
      'A desk lamp',
    )
    await user.type(within(form).getByLabelText('Price (EUR)'), '24.50')
    await user.clear(within(form).getByLabelText('Stock'))
    await user.type(within(form).getByLabelText('Stock'), '3')
    await user.click(within(form).getByRole('button', { name: 'Save product' }))

    const row = (await screen.findByRole('rowheader', { name: 'Test Lamp' }))
      .parentElement!
    expect(row).toHaveTextContent('€24.50')
    expect(screen.queryByRole('region', { name: 'New product' })).toBeNull()
  })

  it('shows validation errors and sends nothing', async () => {
    const onPost = vi.fn()
    server.use(http.post('*/api/products', onPost))
    const { user } = await openAdmin()

    await user.click(screen.getByRole('button', { name: 'Add product' }))
    const form = screen.getByRole('region', { name: 'New product' })
    await user.type(within(form).getByLabelText('URL slug'), 'Not A Slug')
    await user.type(within(form).getByLabelText('Price (EUR)'), '1.999')
    await user.click(within(form).getByRole('button', { name: 'Save product' }))

    expect(
      within(form).getAllByText('This field is required.'),
    ).not.toHaveLength(0)
    expect(within(form).getByLabelText('URL slug')).toHaveAccessibleDescription(
      /lowercase letters/,
    )
    expect(
      within(form).getByLabelText('Price (EUR)'),
    ).toHaveAccessibleDescription('Enter a price like 12.99.')
    expect(onPost).not.toHaveBeenCalled()
  })

  it('edits a product price', async () => {
    const { user } = await openAdmin()

    await user.click(screen.getByRole('button', { name: 'Edit Stoneware Mug' }))
    const form = screen.getByRole('region', { name: 'Edit Stoneware Mug' })
    const price = within(form).getByLabelText('Price (EUR)')
    await user.clear(price)
    await user.type(price, '99.00')
    await user.click(within(form).getByRole('button', { name: 'Save product' }))

    await waitFor(() =>
      expect(
        screen.getByRole('rowheader', { name: 'Stoneware Mug' }).parentElement,
      ).toHaveTextContent('€99.00'),
    )
  })

  it('shows a clear message when the slug is taken', async () => {
    server.use(
      http.put('*/api/products/:id', () =>
        HttpResponse.json(
          { message: 'Slug is already in use' },
          { status: 409 },
        ),
      ),
    )
    const { user } = await openAdmin()

    await user.click(screen.getByRole('button', { name: 'Edit Stoneware Mug' }))
    await user.click(screen.getByRole('button', { name: 'Save product' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Another product already uses this URL slug.',
    )
  })

  it('deletes a product after confirming', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { user } = await openAdmin()

    await user.click(
      screen.getByRole('button', { name: 'Delete Stoneware Mug' }),
    )

    await waitFor(() =>
      expect(
        screen.queryByRole('rowheader', { name: 'Stoneware Mug' }),
      ).not.toBeInTheDocument(),
    )
  })

  it('keeps the product when delete is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { user } = await openAdmin()

    await user.click(
      screen.getByRole('button', { name: 'Delete Stoneware Mug' }),
    )

    expect(
      screen.getByRole('rowheader', { name: 'Stoneware Mug' }),
    ).toBeInTheDocument()
  })
})
