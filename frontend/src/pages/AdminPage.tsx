import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { AdminLogin } from '@/features/admin/components/AdminLogin'
import { AdminProducts } from '@/features/admin/components/AdminProducts'
import { useMe } from '@/features/auth/api'
import { usePageMeta } from '@/hooks/usePageMeta'
import { strings } from '@/lib/i18n/strings'

/**
 * Admin module. Shows the Google login unless an admin is logged in. This guard
 * is only for UX: the backend refuses admin requests from anyone else.
 */
export function AdminPage() {
  usePageMeta(strings.admin.title)
  const me = useMe()

  if (me.isPending) {
    return (
      <div role="status" aria-label={strings.common.loading}>
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }
  if (me.isError) {
    return (
      <ErrorState
        message={strings.admin.login.checkError}
        onRetry={() => me.refetch()}
      />
    )
  }
  return me.data ? <AdminProducts admin={me.data} /> : <AdminLogin />
}
