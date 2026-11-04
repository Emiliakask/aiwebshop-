import { useId, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { inputBaseClasses, inputClasses } from '@/components/ui/FormField'
import { useProducts } from '@/features/products/api'
import {
  ProductGrid,
  ProductGridSkeleton,
} from '@/features/products/components/ProductGrid'
import { parseProductSort, productSorts } from '@/features/products/types'
import { usePageMeta } from '@/hooks/usePageMeta'
import { cn } from '@/lib/cn'
import { strings } from '@/lib/i18n/strings'

const t = strings.products

export function HomePage() {
  usePageMeta(t.title, t.metaDescription)
  const searchId = useId()
  const sortId = useId()

  // Search and sort live in the URL so they can be shared and bookmarked.
  const [searchParams, setSearchParams] = useSearchParams()
  const search = searchParams.get('search') ?? ''
  const sort = parseProductSort(searchParams.get('sort'))
  const products = useProducts({ search, sort })

  function updateParam(key: string, value: string) {
    setSearchParams(
      (params) => {
        if (value) params.set(key, value)
        else params.delete(key)
        return params
      },
      { replace: key === 'sort' },
    )
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = new FormData(event.currentTarget).get('search')
    updateParam('search', typeof value === 'string' ? value.trim() : '')
  }

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">
          {t.title}
        </h1>
        <p className="text-stone-600">{strings.app.tagline}</p>
      </header>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <form
          role="search"
          onSubmit={handleSearch}
          className="flex flex-1 gap-2 sm:max-w-md"
        >
          <label htmlFor={searchId} className="sr-only">
            {t.searchLabel}
          </label>
          <input
            // Re-mount when the URL changes (e.g. back button) to show the right value.
            key={search}
            id={searchId}
            name="search"
            type="search"
            defaultValue={search}
            placeholder={t.searchPlaceholder}
            className={inputClasses}
          />
          <Button type="submit" variant="secondary">
            {t.searchButton}
          </Button>
        </form>

        <div className="flex items-center gap-2">
          <label htmlFor={sortId} className="text-sm whitespace-nowrap">
            {t.sortLabel}
          </label>
          <select
            id={sortId}
            value={sort}
            onChange={(e) =>
              updateParam(
                'sort',
                e.target.value === 'featured' ? '' : e.target.value,
              )
            }
            className={inputBaseClasses}
          >
            {productSorts.map((option) => (
              <option key={option} value={option}>
                {t.sort[option]}
              </option>
            ))}
          </select>
        </div>
      </div>

      {products.isPending ? (
        <ProductGridSkeleton />
      ) : products.isError ? (
        <ErrorState message={t.loadError} onRetry={() => products.refetch()} />
      ) : products.data.items.length === 0 ? (
        <EmptyState
          title={t.emptyTitle}
          body={t.emptyBody}
          action={
            search && (
              <Button
                variant="secondary"
                onClick={() => updateParam('search', '')}
              >
                {t.clearSearch}
              </Button>
            )
          }
        />
      ) : (
        <section
          aria-label={t.resultCount(products.data.total)}
          className={cn(
            'flex flex-col gap-4 transition-opacity',
            products.isPlaceholderData && 'opacity-60',
          )}
        >
          <p className="text-sm text-stone-600" aria-live="polite">
            {t.resultCount(products.data.total)}
          </p>
          <ProductGrid products={products.data.items} />
        </section>
      )}
    </div>
  )
}
