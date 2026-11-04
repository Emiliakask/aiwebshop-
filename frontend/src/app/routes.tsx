import type { RouteObject } from 'react-router'
import { HomePage } from '@/pages/HomePage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { RootLayout } from './RootLayout'
import { RouteError } from './RouteError'

/** The home and 404 pages are bundled eagerly; every other page is loaded on demand. */
export const routes: RouteObject[] = [
  {
    element: <RootLayout />,
    errorElement: <RouteError />,
    children: [
      {
        errorElement: <RouteError />,
        children: [
          { index: true, element: <HomePage /> },
          {
            path: 'products/:slug',
            lazy: () =>
              import('@/pages/ProductPage').then((m) => ({
                Component: m.ProductPage,
              })),
          },
          {
            path: 'cart',
            lazy: () =>
              import('@/pages/CartPage').then((m) => ({
                Component: m.CartPage,
              })),
          },
          {
            path: 'checkout',
            lazy: () =>
              import('@/pages/CheckoutPage').then((m) => ({
                Component: m.CheckoutPage,
              })),
          },
          {
            path: 'orders/:orderId',
            lazy: () =>
              import('@/pages/OrderPage').then((m) => ({
                Component: m.OrderPage,
              })),
          },
          {
            path: 'admin',
            lazy: () =>
              import('@/pages/AdminPage').then((m) => ({
                Component: m.AdminPage,
              })),
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]
