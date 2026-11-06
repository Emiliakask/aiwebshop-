import { Link, NavLink, Outlet, ScrollRestoration } from 'react-router'
import { useLogout, useMe } from '@/features/auth/api'
import { useCart } from '@/features/cart/api'
import { cn } from '@/lib/cn'
import { strings } from '@/lib/i18n/strings'

export function RootLayout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only z-50 rounded-lg bg-white px-4 py-2 focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        {strings.app.skipToContent}
      </a>
      <SiteHeader />
      <main
        id="main"
        tabIndex={-1}
        className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 focus:outline-none sm:px-6 sm:py-12"
      >
        <Outlet />
      </main>
      <footer className="border-t border-stone-200 py-8 text-center text-sm text-stone-500">
        © {new Date().getFullYear()} {strings.app.footer}
      </footer>
      <ScrollRestoration />
    </div>
  )
}

function SiteHeader() {
  const cart = useCart()
  const me = useMe()
  const logout = useLogout()
  const customer = me.data?.type === 'customer' ? me.data : undefined
  const count = cart.data?.itemCount ?? 0

  const navLinkClasses = ({ isActive }: { isActive: boolean }) =>
    cn(
      'rounded-lg px-3 py-2 text-sm font-medium hover:bg-stone-100',
      isActive ? 'text-stone-900' : 'text-stone-600',
    )

  return (
    <header className="sticky top-0 z-40 border-b border-stone-200 bg-stone-50/90 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Link to="/" className="text-lg font-semibold tracking-tight">
          {strings.app.name}
        </Link>
        <nav aria-label={strings.nav.main} className="flex items-center gap-1">
          <NavLink to="/" end className={navLinkClasses}>
            {strings.nav.shop}
          </NavLink>
          <NavLink to="/admin" className={navLinkClasses}>
            {strings.nav.admin}
          </NavLink>
          {customer ? (
            <button
              type="button"
              onClick={() => logout.mutate()}
              className="rounded-lg px-3 py-2 text-sm font-medium text-stone-600 hover:bg-stone-100"
              aria-label={strings.nav.logoutAs(customer.name)}
            >
              {strings.nav.logout}
            </button>
          ) : (
            <NavLink to="/login" className={navLinkClasses}>
              {strings.nav.login}
            </NavLink>
          )}
          <NavLink
            to="/cart"
            className={navLinkClasses}
            aria-label={strings.nav.cartWithCount(count)}
          >
            {strings.nav.cart}
            {count > 0 && (
              <span className="bg-brand-600 ml-1.5 inline-flex min-w-5 items-center justify-center rounded-full px-1.5 text-xs text-white tabular-nums">
                {count}
              </span>
            )}
          </NavLink>
        </nav>
      </div>
    </header>
  )
}
