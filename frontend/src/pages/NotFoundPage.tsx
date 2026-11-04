import { ButtonLink } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { usePageMeta } from '@/hooks/usePageMeta'
import { strings } from '@/lib/i18n/strings'

export function NotFoundPage() {
  usePageMeta(strings.notFound.title)
  return (
    <EmptyState
      title={strings.notFound.title}
      body={strings.notFound.body}
      action={<ButtonLink to="/">{strings.common.continueShopping}</ButtonLink>}
    />
  )
}
