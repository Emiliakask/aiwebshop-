import { useNavigate, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/Button'
import { useLogout, useMe } from '@/features/auth/api'
import { CustomerLogin } from '@/features/auth/components/CustomerLogin'
import { usePageMeta } from '@/hooks/usePageMeta'
import { strings } from '@/lib/i18n/strings'

const t = strings.login

/** Customer login with Smart-ID (docs/auth.md). Admins log in on /admin with Google. */
export function LoginPage() {
  usePageMeta(t.title)
  const me = useMe()
  const logout = useLogout()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  // Only go back to paths inside this shop.
  const next = params.get('next')?.startsWith('/') ? params.get('next')! : '/'

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">{t.title}</h1>
        <p className="text-stone-600">{t.body}</p>
      </div>
      {me.data?.type === 'customer' ? (
        <div className="flex flex-col gap-4 rounded-2xl bg-stone-100 p-6">
          <p>{t.loggedInAs(me.data.name, me.data.personalCode)}</p>
          <Button variant="secondary" onClick={() => logout.mutate()}>
            {strings.nav.logout}
          </Button>
        </div>
      ) : (
        <CustomerLogin onLoggedIn={() => navigate(next, { replace: true })} />
      )}
    </div>
  )
}
