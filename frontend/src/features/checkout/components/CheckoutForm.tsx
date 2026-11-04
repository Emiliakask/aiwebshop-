import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Button } from '@/components/ui/Button'
import { FormField, inputClasses } from '@/components/ui/FormField'
import { StatusMessage } from '@/components/ui/StatusMessage'
import { ApiError } from '@/lib/apiClient'
import { strings } from '@/lib/i18n/strings'
import { APP_LOCALE } from '@/lib/money'
import { useCreateOrder } from '../api'
import {
  checkoutFormSchema,
  shippingCountries,
  type CheckoutFormValues,
  type Order,
} from '../types'

const t = strings.checkout

const countryNames = new Intl.DisplayNames([APP_LOCALE], { type: 'region' })

type CheckoutFormProps = {
  onOrderCreated: (order: Order) => void
}

export function CheckoutForm({ onOrderCreated }: CheckoutFormProps) {
  // One key per checkout attempt: retries after a network error reuse it,
  // so the backend can recognise them and never create a second order.
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const createOrder = useCreateOrder()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CheckoutFormValues>({
    resolver: zodResolver(checkoutFormSchema),
    defaultValues: {
      email: '',
      fullName: '',
      addressLine1: '',
      city: '',
      postalCode: '',
    },
  })

  const onSubmit = handleSubmit((values) => {
    createOrder.mutate(
      { values, idempotencyKey },
      { onSuccess: onOrderCreated },
    )
  })

  // Stays disabled after success too, while the browser leaves for payment.
  const submitting = createOrder.isPending || createOrder.isSuccess

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-8">
      <fieldset className="flex flex-col gap-4">
        <legend className="mb-4 text-lg font-semibold">
          {t.contactHeading}
        </legend>
        <FormField label={t.fields.email} error={errors.email?.message}>
          {(props) => (
            <input
              {...props}
              {...register('email')}
              type="email"
              autoComplete="email"
              className={inputClasses}
            />
          )}
        </FormField>
      </fieldset>

      <fieldset className="flex flex-col gap-4">
        <legend className="mb-4 text-lg font-semibold">
          {t.shippingHeading}
        </legend>
        <FormField label={t.fields.fullName} error={errors.fullName?.message}>
          {(props) => (
            <input
              {...props}
              {...register('fullName')}
              autoComplete="name"
              className={inputClasses}
            />
          )}
        </FormField>
        <FormField
          label={t.fields.addressLine1}
          error={errors.addressLine1?.message}
        >
          {(props) => (
            <input
              {...props}
              {...register('addressLine1')}
              autoComplete="address-line1"
              className={inputClasses}
            />
          )}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t.fields.city} error={errors.city?.message}>
            {(props) => (
              <input
                {...props}
                {...register('city')}
                autoComplete="address-level2"
                className={inputClasses}
              />
            )}
          </FormField>
          <FormField
            label={t.fields.postalCode}
            error={errors.postalCode?.message}
          >
            {(props) => (
              <input
                {...props}
                {...register('postalCode')}
                autoComplete="postal-code"
                className={inputClasses}
              />
            )}
          </FormField>
        </div>
        <FormField label={t.fields.country} error={errors.country?.message}>
          {(props) => (
            <select
              {...props}
              {...register('country')}
              autoComplete="country"
              defaultValue=""
              className={inputClasses}
            >
              <option value="" disabled>
                {t.selectCountry}
              </option>
              {shippingCountries.map((code) => (
                <option key={code} value={code}>
                  {countryNames.of(code)}
                </option>
              ))}
            </select>
          )}
        </FormField>
      </fieldset>

      {createOrder.isError && (
        <StatusMessage tone="error">
          {createOrder.error instanceof ApiError &&
          createOrder.error.status === 409
            ? t.outOfStockError
            : t.orderError}
        </StatusMessage>
      )}

      <div className="flex flex-col gap-3">
        <Button type="submit" size="lg" disabled={submitting}>
          {submitting ? t.placingOrder : t.placeOrder}
        </Button>
        <p className="text-sm text-stone-600">{t.paymentNote}</p>
      </div>
    </form>
  )
}
