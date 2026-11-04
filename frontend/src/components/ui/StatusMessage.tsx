import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type Tone = 'error' | 'warning' | 'success'

const tones: Record<Tone, string> = {
  error: 'border-red-200 bg-red-50 text-red-800',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
}

type StatusMessageProps = {
  tone: Tone
  children: ReactNode
  className?: string
}

/** Inline message. Errors are announced immediately, others politely. */
export function StatusMessage({
  tone,
  children,
  className,
}: StatusMessageProps) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn(
        'rounded-lg border px-4 py-3 text-sm',
        tones[tone],
        className,
      )}
    >
      {children}
    </div>
  )
}
