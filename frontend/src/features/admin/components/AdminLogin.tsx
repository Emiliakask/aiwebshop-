import { useSearchParams } from 'react-router'
import { ButtonLink } from '@/components/ui/Button'
import { StatusMessage } from '@/components/ui/StatusMessage'
import { ADMIN_LOGIN_URL } from '@/features/auth/api'
import { strings } from '@/lib/i18n/strings'

const t = strings.admin.login

/** Shown on /admin when nobody is logged in. Login happens on Google, via the backend. */
export function AdminLogin() {
  const [searchParams] = useSearchParams()
  const loginFailed = searchParams.has('loginError')

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6 text-center">
      <h1 className="text-3xl font-semibold tracking-tight">{t.title}</h1>
      <p className="text-stone-600">{t.body}</p>
      {loginFailed && <StatusMessage tone="error">{t.failed}</StatusMessage>}
      <ButtonLink
        to={ADMIN_LOGIN_URL}
        reloadDocument
        size="lg"
        className="self-center"
      >
        {t.button}
      </ButtonLink>
    </div>
  )
}
