import { expect, test } from '@playwright/test'

test('browse → product → add to cart → checkout → order paid', async ({
  page,
}) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'Serving Bowl' }).click()

  await expect(
    page.getByRole('heading', { name: 'Serving Bowl', level: 1 }),
  ).toBeVisible()
  await expect(page.getByText('Only 3 left')).toBeVisible()
  await page.getByRole('button', { name: 'Add to cart' }).click()
  await expect(page.getByText('Added to your cart.')).toBeVisible()

  await page.getByRole('link', { name: 'View cart' }).click()
  await expect(page.getByRole('heading', { name: 'Your cart' })).toBeVisible()
  await page.getByRole('link', { name: 'Go to checkout' }).click()

  await page.getByLabel('Email').fill('ada@example.com')
  await page.getByLabel('Full name').fill('Ada Lovelace')
  await page.getByLabel('Mobile phone').fill('+372 5123 4567')
  await page.getByLabel('Country').selectOption('EE')
  await page.getByLabel('Search parcel machines').fill('akadeemia')
  await page.getByLabel('Parcel machine', { exact: true }).selectOption('96003')
  await page.getByRole('button', { name: 'Place order and pay' }).click()

  await expect(
    page.getByRole('heading', { name: 'Confirming your payment…' }),
  ).toBeVisible()
  // The order page polls the backend until the payment is confirmed.
  await expect(
    page.getByRole('heading', { name: 'Thank you for your order!' }),
  ).toBeVisible({ timeout: 10_000 })
  await expect(page.getByRole('link', { name: 'Cart, 0 items' })).toBeVisible()
})

test('out-of-stock products cannot be added to the cart', async ({ page }) => {
  await page.goto('/products/brass-pen')
  await expect(page.getByRole('button', { name: 'Unavailable' })).toBeDisabled()
})
