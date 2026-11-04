import { useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/Button'
import { StatusMessage } from '@/components/ui/StatusMessage'
import type { Product } from '@/features/products/types'
import { strings } from '@/lib/i18n/strings'
import { useAddToCart } from '../api'
import { QuantitySelect } from './QuantitySelect'

const t = strings.product

export function AddToCartForm({ product }: { product: Product }) {
  const quantityId = useId()
  const [quantity, setQuantity] = useState(1)
  const addToCart = useAddToCart()
  const soldOut = product.stock === 0

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    addToCart.mutate({ productId: product.id, quantity })
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {!soldOut && (
        <div className="flex items-center gap-3">
          <label htmlFor={quantityId} className="text-sm font-medium">
            {t.quantityLabel}
          </label>
          <QuantitySelect
            id={quantityId}
            stock={product.stock}
            value={quantity}
            onChange={(e) => setQuantity(Number(e.target.value))}
          />
        </div>
      )}

      <Button
        type="submit"
        size="lg"
        disabled={soldOut || addToCart.isPending}
        className="w-full sm:w-auto"
      >
        {soldOut
          ? strings.products.outOfStock
          : addToCart.isPending
            ? t.adding
            : t.addToCart}
      </Button>

      {addToCart.isSuccess && (
        <StatusMessage tone="success">
          {t.added}{' '}
          <Link to="/cart" className="font-medium underline">
            {t.viewCart}
          </Link>
        </StatusMessage>
      )}
      {addToCart.isError && (
        <StatusMessage tone="error">{t.addError}</StatusMessage>
      )}
    </form>
  )
}
