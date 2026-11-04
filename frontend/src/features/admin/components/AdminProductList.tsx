import { Button } from '@/components/ui/Button'
import { PriceTag } from '@/components/ui/PriceTag'
import { StatusMessage } from '@/components/ui/StatusMessage'
import type { Product } from '@/features/products/types'
import { strings } from '@/lib/i18n/strings'
import { useDeleteProduct } from '../api'

const t = strings.admin

type AdminProductListProps = {
  products: Product[]
  onEdit: (id: string) => void
}

export function AdminProductList({ products, onEdit }: AdminProductListProps) {
  const deleteProduct = useDeleteProduct()

  function handleDelete(product: Product) {
    if (window.confirm(t.confirmDelete(product.name))) {
      deleteProduct.mutate(product.id)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {deleteProduct.isError && (
        <StatusMessage tone="error">{t.deleteError}</StatusMessage>
      )}
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-stone-200 text-stone-600">
            <tr>
              <th scope="col" className="py-2 pr-4 font-medium">
                {t.fields.name}
              </th>
              <th scope="col" className="py-2 pr-4 font-medium">
                {t.columns.price}
              </th>
              <th scope="col" className="py-2 pr-4 font-medium">
                {t.fields.stock}
              </th>
              <th scope="col" className="py-2 font-medium">
                <span className="sr-only">{t.columns.actions}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id} className="border-b border-stone-100">
                <th scope="row" className="py-3 pr-4 font-medium">
                  {product.name}
                </th>
                <td className="py-3 pr-4">
                  <PriceTag price={product.price} />
                </td>
                <td className="py-3 pr-4 tabular-nums">{product.stock}</td>
                <td className="py-3">
                  <div className="flex justify-end gap-2">
                    <Button
                      variant="secondary"
                      onClick={() => onEdit(product.id)}
                      aria-label={t.editLabel(product.name)}
                    >
                      {t.edit}
                    </Button>
                    <Button
                      variant="ghost"
                      onClick={() => handleDelete(product)}
                      disabled={
                        deleteProduct.isPending &&
                        deleteProduct.variables === product.id
                      }
                      aria-label={t.deleteLabel(product.name)}
                    >
                      {t.delete}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
