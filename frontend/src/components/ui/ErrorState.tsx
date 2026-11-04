import { strings } from '@/lib/i18n/strings'
import { Button } from './Button'
import { StatusMessage } from './StatusMessage'

type ErrorStateProps = {
  message: string
  onRetry?: () => void
}

export function ErrorState({ message, onRetry }: ErrorStateProps) {
  return (
    <StatusMessage
      tone="error"
      className="flex flex-wrap items-center justify-between gap-3"
    >
      <p>{message}</p>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          {strings.common.retry}
        </Button>
      )}
    </StatusMessage>
  )
}
