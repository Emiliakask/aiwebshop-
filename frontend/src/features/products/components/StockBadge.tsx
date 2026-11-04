import { strings } from '@/lib/i18n/strings'
import { cn } from '@/lib/cn'
import { LOW_STOCK_THRESHOLD } from '../types'

type StockBadgeProps = {
  stock: number
  showInStock?: boolean
  className?: string
}

export function StockBadge({ stock, showInStock, className }: StockBadgeProps) {
  if (stock === 0) {
    return (
      <span className={cn('text-sm font-medium text-stone-500', className)}>
        {strings.products.outOfStock}
      </span>
    )
  }
  if (stock <= LOW_STOCK_THRESHOLD) {
    return (
      <span className={cn('text-sm font-medium text-amber-700', className)}>
        {strings.products.lowStock(stock)}
      </span>
    )
  }
  if (!showInStock) return null
  return (
    <span className={cn('text-sm font-medium text-emerald-700', className)}>
      {strings.products.inStock}
    </span>
  )
}
