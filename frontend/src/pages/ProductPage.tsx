import { Link, useParams } from 'react-router'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { PriceTag } from '@/components/ui/PriceTag'
import { Skeleton } from '@/components/ui/Skeleton'
import { AddToCartForm } from '@/features/cart/components/AddToCartForm'
import { useProduct } from '@/features/products/api'
import { StockBadge } from '@/features/products/components/StockBadge'
import { usePageMeta } from '@/hooks/usePageMeta'
import { ApiError } from '@/lib/apiClient'
import { strings } from '@/lib/i18n/strings'
import { ButtonLink } from '@/components/ui/Button'

const t = strings.product

export function ProductPage() {
  const { slug = '' } = useParams()
  const product = useProduct(slug)
  usePageMeta(product.data?.name, product.data?.description)

  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/"
        className="self-start text-sm text-stone-600 hover:text-stone-900 hover:underline"
      >
        ← {t.backToShop}
      </Link>

      {product.isPending ? (
        <ProductSkeleton />
      ) : product.isError ? (
        product.error instanceof ApiError && product.error.status === 404 ? (
          <EmptyState
            title={t.notFoundTitle}
            body={t.notFoundBody}
            action={
              <ButtonLink to="/" variant="secondary">
                {strings.common.continueShopping}
              </ButtonLink>
            }
          />
        ) : (
          <ErrorState message={t.loadError} onRetry={() => product.refetch()} />
        )
      ) : (
        <article className="grid gap-8 md:grid-cols-2 md:gap-12">
          <div className="aspect-square overflow-hidden rounded-2xl bg-stone-100">
            <img
              src={product.data.imageUrl}
              alt={product.data.imageAlt}
              width={800}
              height={800}
              className="size-full object-cover"
            />
          </div>
          <div className="flex flex-col gap-6">
            <div className="flex flex-col gap-2">
              <p className="text-sm tracking-wide text-stone-500 uppercase">
                {product.data.category}
              </p>
              <h1 className="text-3xl font-semibold tracking-tight">
                {product.data.name}
              </h1>
              <PriceTag
                price={product.data.price}
                className="text-2xl text-stone-900"
              />
              <StockBadge stock={product.data.stock} showInStock />
            </div>
            <p className="leading-relaxed text-stone-700">
              {product.data.description}
            </p>
            <AddToCartForm product={product.data} />
          </div>
        </article>
      )}
    </div>
  )
}

function ProductSkeleton() {
  return (
    <div
      className="grid gap-8 md:grid-cols-2 md:gap-12"
      role="status"
      aria-label={strings.common.loading}
    >
      <Skeleton className="aspect-square rounded-2xl" />
      <div className="flex flex-col gap-4">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-9 w-3/4" />
        <Skeleton className="h-7 w-28" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-12 w-40" />
      </div>
    </div>
  )
}
