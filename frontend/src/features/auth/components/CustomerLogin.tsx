import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { FormField, inputClasses } from '@/components/ui/FormField'
import { ApiError } from '@/lib/apiClient'
import { strings } from '@/lib/i18n/strings'
import { APP_LOCALE } from '@/lib/money'
import {
  authKeys,
  LOGIN_POLL_MS,
  pollLoginStatus,
  useSmartIdConfig,
  useStartChooseNumber,
  useTestAccountLogin,
} from '../api'
import {
  isValidPersonalCode,
  smartIdCountries,
  type Customer,
  type SmartIdCountry,
} from '../types'

const t = strings.login
const countryNames = new Intl.DisplayNames([APP_LOCALE], { type: 'region' })

/**
 * Smart-ID "choose number": personal code -> code shown here -> the user picks the same code in the
 * Smart-ID app. Who logged in comes only from the backend, after it has verified SK's answer.
 */
export function CustomerLogin({
  onLoggedIn,
}: {
  onLoggedIn: (me: Customer) => void
}) {
  const config = useSmartIdConfig()
  const start = useStartChooseNumber()
  const testLogin = useTestAccountLogin()
  const [country, setCountry] = useState<SmartIdCountry>('EE')
  const [personalCode, setPersonalCode] = useState('')
  const [fieldError, setFieldError] = useState<string>()
  const [problem, setProblem] = useState<string>()
  /** Set while the Smart-ID app is asked; a new value per attempt restarts polling. */
  const [attempt, setAttempt] = useState<{ id: number; code: string }>()

  function submit(event: FormEvent) {
    event.preventDefault()
    setProblem(undefined)
    if (!isValidPersonalCode(country, personalCode)) {
      setFieldError(t.errors.personalCode)
      return
    }
    setFieldError(undefined)
    start.mutate(
      { country, personalCode: personalCode.trim() },
      {
        onSuccess: ({ verificationCode }) =>
          setAttempt({ id: Date.now(), code: verificationCode }),
        onError: (error) =>
          setProblem(
            error instanceof ApiError && error.status === 404
              ? t.errors.notFound
              : t.errors.unavailable,
          ),
      },
    )
  }

  if (attempt) {
    return (
      <WaitingForApp
        key={attempt.id}
        verificationCode={attempt.code}
        onDone={(result) => {
          setAttempt(undefined)
          if (result.ok) onLoggedIn(result.me)
          else setProblem(result.message)
        }}
      />
    )
  }

  return (
    <div className="flex flex-col gap-6">
      {problem && (
        <p
          role="alert"
          className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-800"
        >
          {problem}
        </p>
      )}
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <FormField label={t.country}>
          {(props) => (
            <select
              {...props}
              value={country}
              onChange={(e) => setCountry(e.target.value as SmartIdCountry)}
              className={inputClasses}
            >
              {smartIdCountries.map((code) => (
                <option key={code} value={code}>
                  {countryNames.of(code)}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <FormField label={t.personalCode} error={fieldError}>
          {(props) => (
            <input
              {...props}
              value={personalCode}
              onChange={(e) => setPersonalCode(e.target.value)}
              inputMode="numeric"
              autoComplete="off"
              placeholder={country === 'LV' ? '123456-12345' : '38001010000'}
              className={inputClasses}
            />
          )}
        </FormField>
        <Button type="submit" size="lg" disabled={start.isPending}>
          {start.isPending ? t.starting : t.submit}
        </Button>
      </form>

      {config.data?.testAccount && (
        <div className="flex flex-col gap-2 border-t border-stone-200 pt-6">
          <p className="text-sm text-stone-600">{t.testAccountHint}</p>
          <Button
            variant="secondary"
            disabled={testLogin.isPending}
            onClick={() =>
              testLogin.mutate(undefined, {
                onSuccess: onLoggedIn,
                onError: () => setProblem(t.errors.unavailable),
              })
            }
          >
            {t.testAccount}
          </Button>
        </div>
      )}
    </div>
  )
}

type Done = { ok: true; me: Customer } | { ok: false; message: string }

function WaitingForApp({
  verificationCode,
  onDone,
}: {
  verificationCode: string
  onDone: (result: Done) => void
}) {
  const queryClient = useQueryClient()
  const status = useQuery({
    queryKey: ['auth', 'smart-id', 'status', verificationCode],
    queryFn: pollLoginStatus,
    refetchInterval: (query) =>
      query.state.data?.state === 'running' ? LOGIN_POLL_MS : false,
    refetchOnWindowFocus: false,
    retry: false,
    gcTime: 0,
  })

  useEffect(() => {
    if (status.isError) {
      onDone({ ok: false, message: t.errors.unavailable })
      return
    }
    const data = status.data
    if (!data || data.state === 'running') return
    if (data.state === 'complete') {
      queryClient.setQueryData(authKeys.me, data.me)
      onDone({ ok: true, me: data.me })
    } else {
      onDone({ ok: false, message: t.errors[data.reason] })
    }
  }, [status.data, status.isError, onDone, queryClient])

  return (
    <div
      className="flex flex-col items-center gap-4 text-center"
      aria-live="polite"
    >
      <p className="text-stone-700">{t.waiting}</p>
      <p
        className="font-mono text-5xl font-semibold tracking-widest tabular-nums"
        aria-label={t.codeLabel(verificationCode)}
      >
        {verificationCode}
      </p>
      <p className="text-sm text-stone-600">{t.waitingHint}</p>
      <Button
        variant="ghost"
        onClick={() => onDone({ ok: false, message: t.cancelled })}
      >
        {t.cancel}
      </Button>
    </div>
  )
}
