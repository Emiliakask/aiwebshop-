import { cn } from '@/lib/cn'
import { formatMoney, type Money } from '@/lib/money'

type PriceTagProps = {
  price: Money
  className?: string
}

export function PriceTag({ price, className }: PriceTagProps) {
  return (
    <span className={cn('tabular-nums', className)}>{formatMoney(price)}</span>
  )
}
