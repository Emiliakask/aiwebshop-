import { isRouteErrorResponse, useRouteError } from 'react-router'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { strings } from '@/lib/i18n/strings'
import { NotFoundPage } from '@/pages/NotFoundPage'

/** Catches render errors and failed lazy imports anywhere in the app. */
export function RouteError() {
  const error = useRouteError()

  if (isRouteErrorResponse(error) && error.status === 404) {
    return <NotFoundPage />
  }

  console.error(error)
  return (
    <div className="mx-auto max-w-xl px-4 py-16">
      <EmptyState
        title={strings.routeError.title}
        body={strings.routeError.body}
        action={
          <Button onClick={() => window.location.reload()}>
            {strings.routeError.reload}
          </Button>
        }
      />
    </div>
  )
}
