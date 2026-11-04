import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Button } from '@/components/ui/Button'
import { FormField, inputClasses } from '@/components/ui/FormField'
import { StatusMessage } from '@/components/ui/StatusMessage'
import type { Product } from '@/features/products/types'
import { ApiError } from '@/lib/apiClient'
import { strings } from '@/lib/i18n/strings'
import { SHOP_CURRENCY } from '@/lib/money'
import { useSaveProduct } from '../api'
import {
  productFormSchema,
  toFormValues,
  toProductInput,
  type ProductFormValues,
} from '../types'

const t = strings.admin

/** Plain text inputs, in the order they appear in the form. */
const textFields = ['name', 'slug', 'category', 'imageUrl', 'imageAlt'] as const

type ProductFormProps = {
  /** Missing = add a new product. */
  product?: Product
  onDone: () => void
}

export function ProductForm({ product, onDone }: ProductFormProps) {
  const saveProduct = useSaveProduct()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: toFormValues(product),
  })

  const onSubmit = handleSubmit((values) => {
    saveProduct.mutate(
      { id: product?.id, input: toProductInput(values) },
      { onSuccess: onDone },
    )
  })

  const saveError =
    saveProduct.error instanceof ApiError && saveProduct.error.status === 409
      ? t.slugTaken
      : t.saveError

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div className="grid gap-4 sm:grid-cols-2">
        {textFields.map((name) => (
          <FormField
            key={name}
            label={t.fields[name]}
            error={errors[name]?.message}
          >
            {(props) => (
              <input {...props} {...register(name)} className={inputClasses} />
            )}
          </FormField>
        ))}
        <FormField
          label={t.fields.price(SHOP_CURRENCY)}
          error={errors.price?.message}
        >
          {(props) => (
            <input
              {...props}
              {...register('price')}
              inputMode="decimal"
              className={inputClasses}
            />
          )}
        </FormField>
        <FormField label={t.fields.stock} error={errors.stock?.message}>
          {(props) => (
            <input
              {...props}
              {...register('stock', { valueAsNumber: true })}
              type="number"
              min={0}
              step={1}
              className={inputClasses}
            />
          )}
        </FormField>
      </div>
      <FormField
        label={t.fields.description}
        error={errors.description?.message}
      >
        {(props) => (
          <textarea
            {...props}
            {...register('description')}
            rows={4}
            className={inputClasses}
          />
        )}
      </FormField>

      {saveProduct.isError && (
        <StatusMessage tone="error">{saveError}</StatusMessage>
      )}

      <div className="flex gap-3">
        <Button type="submit" disabled={saveProduct.isPending}>
          {saveProduct.isPending ? t.saving : t.save}
        </Button>
        <Button variant="secondary" onClick={onDone}>
          {t.cancel}
        </Button>
      </div>
    </form>
  )
}
