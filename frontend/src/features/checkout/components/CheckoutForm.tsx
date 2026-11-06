import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { Button } from '@/components/ui/Button'
import { FormField, inputClasses } from '@/components/ui/FormField'
import { StatusMessage } from '@/components/ui/StatusMessage'
import { ApiError } from '@/lib/apiClient'
import { strings } from '@/lib/i18n/strings'
import { useCreateOrder } from '../api'
import {
  checkoutFormSchema,
  type CheckoutFormValues,
  type Order,
} from '../types'
import { DeliveryFields } from './DeliveryFields'

const t = strings.checkout

type CheckoutFormProps = {
  onOrderCreated: (order: Order) => void
}

export function CheckoutForm({ onOrderCreated }: CheckoutFormProps) {
  // One key per checkout attempt: retries after a network error reuse it,
  // so the backend can recognise them and never create a second order.
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const createOrder = useCreateOrder()

  const form = useForm<CheckoutFormValues>({
    resolver: zodResolver(checkoutFormSchema),
    defaultValues: {
      email: '',
      fullName: '',
      phone: '',
      carrierCode: '',
      pickupPointId: '',
    },
  })
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = form

  const onSubmit = handleSubmit((values) => {
    createOrder.mutate(
      { values, idempotencyKey },
      { onSuccess: onOrderCreated },
    )
  })

  // Stays disabled after success too, while the browser leaves for payment.
  const submitting = createOrder.isPending || createOrder.isSuccess

  return (
    <FormProvider {...form}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-8">
        <fieldset className="flex flex-col gap-4">
          <legend className="mb-4 text-lg font-semibold">
            {t.contactHeading}
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
          <FormField label={t.fields.phone} error={errors.phone?.message}>
            {(props) => (
              <input
                {...props}
                {...register('phone')}
                type="tel"
                autoComplete="tel"
                placeholder="+372 5123 4567"
                className={inputClasses}
              />
            )}
          </FormField>
          <p className="-mt-2 text-sm text-stone-600">{t.phoneHint}</p>
        </fieldset>

        <fieldset className="flex flex-col gap-4">
          <legend className="mb-4 text-lg font-semibold">
            {t.shippingHeading}
          </legend>
          <DeliveryFields />
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
    </FormProvider>
  )
}
