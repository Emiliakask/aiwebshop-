import type { ComponentProps } from 'react'
import { inputBaseClasses } from '@/components/ui/FormField'
import { cn } from '@/lib/cn'
import { MAX_QUANTITY_PER_ITEM } from '../types'

type QuantitySelectProps = Omit<ComponentProps<'select'>, 'children'> & {
  stock: number
  /** Always offered, even if above stock, so the current value stays visible. */
  current?: number
}

export function QuantitySelect({
  stock,
  current = 1,
  className,
  ...props
}: QuantitySelectProps) {
  const max = Math.max(Math.min(stock, MAX_QUANTITY_PER_ITEM), current, 1)
  return (
    <select className={cn(inputBaseClasses, 'w-20', className)} {...props}>
      {Array.from({ length: max }, (_, i) => i + 1).map((n) => (
        <option key={n} value={n}>
          {n}
        </option>
      ))}
    </select>
  )
}
