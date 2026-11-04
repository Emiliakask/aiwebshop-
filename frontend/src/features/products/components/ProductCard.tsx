import { Link } from 'react-router'
import { PriceTag } from '@/components/ui/PriceTag'
import type { Product } from '../types'
import { StockBadge } from './StockBadge'

type ProductCardProps = {
  product: Product
}

export function ProductCard({ product }: ProductCardProps) {
  const soldOut = product.stock === 0
  return (
    <article
      className="group relative flex flex-col gap-3"
      data-unavailable={soldOut || undefined}
    >
      <div className="aspect-square overflow-hidden rounded-xl bg-stone-100">
        <img
          src={product.imageUrl}
          alt={product.imageAlt}
          width={600}
          height={600}
          loading="lazy"
          className="size-full object-cover transition-transform duration-300 group-hover:scale-105 data-[sold-out=true]:opacity-50 data-[sold-out=true]:grayscale"
          data-sold-out={soldOut}
        />
      </div>
      <div className="flex flex-col gap-1">
        <h3 className="font-medium text-stone-900">
          {/* The ::after makes the whole card clickable while keeping one link. */}
          <Link
            to={`/products/${product.slug}`}
            className="group-has-[:focus-visible]:underline after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none"
          >
            {product.name}
          </Link>
        </h3>
        <div className="flex items-baseline justify-between gap-2">
          <PriceTag
            price={product.price}
            className={soldOut ? 'text-stone-400' : 'text-stone-700'}
          />
          <StockBadge stock={product.stock} />
        </div>
      </div>
    </article>
  )
}
