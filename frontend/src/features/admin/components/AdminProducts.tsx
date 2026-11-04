import { useId, useState } from 'react'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useLogout } from '@/features/auth/api'
import type { Me } from '@/features/auth/types'
import { useProducts } from '@/features/products/api'
import { strings } from '@/lib/i18n/strings'
import { AdminProductList } from './AdminProductList'
import { ProductForm } from './ProductForm'

const t = strings.admin

type Editing = { mode: 'new' } | { mode: 'edit'; id: string } | null

export function AdminProducts({ admin }: { admin: Me }) {
  const products = useProducts({ search: '', sort: 'name' })
  const logout = useLogout()
  const [editing, setEditing] = useState<Editing>(null)
  const formHeadingId = useId()

  const editingProduct =
    editing?.mode === 'edit'
      ? products.data?.items.find((p) => p.id === editing.id)
      : undefined
  const showForm =
    editing?.mode === 'new' || (editing?.mode === 'edit' && editingProduct)

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-3xl font-semibold tracking-tight">{t.title}</h1>
          <p className="text-sm text-stone-600">
            {t.login.loggedInAs(admin.email)}
          </p>
        </div>
        <div className="flex gap-3">
          <Button onClick={() => setEditing({ mode: 'new' })}>
            {t.addProduct}
          </Button>
          <Button
            variant="secondary"
            disabled={logout.isPending}
            onClick={() => logout.mutate()}
          >
            {t.login.logout}
          </Button>
        </div>
      </header>

      {showForm && (
        <section
          aria-labelledby={formHeadingId}
          className="flex flex-col gap-4 rounded-2xl border border-stone-200 bg-white p-4 sm:p-6"
        >
          <h2 id={formHeadingId} className="text-lg font-semibold">
            {editingProduct ? t.editHeading(editingProduct.name) : t.newHeading}
          </h2>
          <ProductForm
            key={editingProduct?.id ?? 'new'}
            product={editingProduct}
            onDone={() => setEditing(null)}
          />
        </section>
      )}

      {products.isPending ? (
        <div role="status" aria-label={strings.common.loading}>
          <Skeleton className="h-48 w-full" />
        </div>
      ) : products.isError ? (
        <ErrorState
          message={strings.products.loadError}
          onRetry={() => products.refetch()}
        />
      ) : products.data.items.length === 0 ? (
        <EmptyState title={t.emptyTitle} body={t.emptyBody} />
      ) : (
        <AdminProductList
          products={products.data.items}
          onEdit={(id) => setEditing({ mode: 'edit', id })}
        />
      )}
    </div>
  )
}
