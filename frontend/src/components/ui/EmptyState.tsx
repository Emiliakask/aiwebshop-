import type { ReactNode } from 'react'

type EmptyStateProps = {
  title: string
  body?: string
  action?: ReactNode
}

export function EmptyState({ title, body, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-stone-300 px-6 py-16 text-center">
      <h2 className="text-lg font-semibold text-stone-900">{title}</h2>
      {body && <p className="max-w-sm text-stone-600">{body}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}
