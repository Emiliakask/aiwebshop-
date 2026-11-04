import { Skeleton } from '@/components/ui/Skeleton'
import { strings } from '@/lib/i18n/strings'
import type { Product } from '../types'
import { ProductCard } from './ProductCard'

const gridClasses =
  'grid grid-cols-2 gap-x-4 gap-y-8 sm:gap-x-6 md:grid-cols-3 lg:grid-cols-4'

export function ProductGrid({ products }: { products: Product[] }) {
  return (
    <ul className={gridClasses}>
      {products.map((product) => (
        <li key={product.id}>
          <ProductCard product={product} />
        </li>
      ))}
    </ul>
  )
}

export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  const items = Array.from({ length: count }, (_, i) => (
    <div key={i} className="flex flex-col gap-3">
      <Skeleton className="aspect-square rounded-xl" />
      <Skeleton className="h-4 w-3/4" />
      <Skeleton className="h-4 w-1/3" />
    </div>
  ))
  return (
    <div
      className={gridClasses}
      role="status"
      aria-label={strings.common.loading}
    >
      {items}
    </div>
  )
}
