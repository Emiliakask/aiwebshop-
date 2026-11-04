import { Link } from 'react-router'
import { Button } from '@/components/ui/Button'
import { PriceTag } from '@/components/ui/PriceTag'
import { strings } from '@/lib/i18n/strings'
import { formatMoney } from '@/lib/money'
import { useRemoveCartItem, useUpdateCartItem } from '../api'
import { hasBlockingIssue, type CartItem } from '../types'
import { QuantitySelect } from './QuantitySelect'

const t = strings.cart

function issueText(item: CartItem): string | null {
  switch (item.issue) {
    case 'out_of_stock':
      return t.issues.out_of_stock
    case 'insufficient_stock':
      return t.issues.insufficient_stock(item.stock)
    case 'price_changed':
      return t.issues.price_changed
    case null:
      return null
  }
}

type CartLineProps = {
  item: CartItem
  onError: () => void
}

export function CartLine({ item, onError }: CartLineProps) {
  const updateItem = useUpdateCartItem()
  const removeItem = useRemoveCartItem()
  const issue = issueText(item)
  const blocking = hasBlockingIssue(item)

  return (
    <li className="flex gap-4 py-6">
      <img
        src={item.imageUrl}
        alt={item.imageAlt}
        width={96}
        height={96}
        className="size-24 shrink-0 rounded-lg bg-stone-100 object-cover"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
          <div>
            <h3 className="font-medium">
              <Link to={`/products/${item.slug}`} className="hover:underline">
                {item.name}
              </Link>
            </h3>
            <p className="text-sm text-stone-600">
              {t.unitPrice(formatMoney(item.unitPrice))}
            </p>
          </div>
          <PriceTag price={item.lineTotal} className="font-medium" />
        </div>

        {issue && (
          <p
            className={
              blocking ? 'text-sm text-red-700' : 'text-sm text-amber-800'
            }
          >
            {issue}
          </p>
        )}

        <div className="flex items-center gap-3">
          {item.issue !== 'out_of_stock' && (
            <QuantitySelect
              aria-label={t.quantityLabel(item.name)}
              stock={item.stock}
              current={item.quantity}
              value={item.quantity}
              disabled={updateItem.isPending}
              onChange={(e) =>
                updateItem.mutate(
                  {
                    productId: item.productId,
                    quantity: Number(e.target.value),
                  },
                  { onError },
                )
              }
            />
          )}
          <Button
            variant="ghost"
            aria-label={t.removeLabel(item.name)}
            disabled={removeItem.isPending}
            onClick={() => removeItem.mutate(item.productId, { onError })}
          >
            {t.remove}
          </Button>
        </div>
      </div>
    </li>
  )
}
