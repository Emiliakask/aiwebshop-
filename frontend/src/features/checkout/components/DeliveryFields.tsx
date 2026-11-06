import { useEffect } from 'react'
import { useFormContext } from 'react-hook-form'
import { ErrorState } from '@/components/ui/ErrorState'
import { FormField, inputClasses } from '@/components/ui/FormField'
import { Skeleton } from '@/components/ui/Skeleton'
import { usePickupPoints, useShippingMethods } from '@/features/shipping/api'
import { ParcelMachinePicker } from '@/features/shipping/components/ParcelMachinePicker'
import { strings } from '@/lib/i18n/strings'
import { APP_LOCALE, formatMoney } from '@/lib/money'
import { shippingCountries, type CheckoutFormValues } from '../types'

const t = strings.shipping
const countryNames = new Intl.DisplayNames([APP_LOCALE], { type: 'region' })

/** Country → delivery method (from the backend) → parcel machine. */
export function DeliveryFields() {
  const {
    register,
    watch,
    setValue,
    formState: { errors },
  } = useFormContext<CheckoutFormValues>()
  const country = watch('country')
  const carrierCode = watch('carrierCode')
  const pickupPointId = watch('pickupPointId')

  const methods = useShippingMethods(country)
  const points = usePickupPoints(carrierCode || undefined, country)

  // A new country means new carriers and machines: start the choice over.
  useEffect(() => {
    setValue('carrierCode', '')
    setValue('pickupPointId', '')
  }, [country, setValue])

  // With only one carrier there is nothing to choose.
  const onlyMethod = methods.data?.length === 1 ? methods.data[0] : undefined
  useEffect(() => {
    if (onlyMethod && !carrierCode) {
      setValue('carrierCode', onlyMethod.carrierCode, { shouldValidate: true })
    }
  }, [onlyMethod, carrierCode, setValue])

  return (
    <div className="flex flex-col gap-4">
      <FormField
        label={strings.checkout.fields.country}
        error={errors.country?.message}
      >
        {(props) => (
          <select
            {...props}
            {...register('country')}
            autoComplete="country"
            className={inputClasses}
          >
            <option value="" disabled>
              {strings.checkout.selectCountry}
            </option>
            {shippingCountries.map((code) => (
              <option key={code} value={code}>
                {countryNames.of(code)}
              </option>
            ))}
          </select>
        )}
      </FormField>

      {country &&
        (methods.isPending ? (
          <Skeleton className="h-12" />
        ) : methods.isError ? (
          <ErrorState
            message={t.methodsError}
            onRetry={() => methods.refetch()}
          />
        ) : methods.data.length === 0 ? (
          <p className="text-sm text-stone-700">{t.noMethods}</p>
        ) : (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium text-stone-800">
              {t.methodLegend}
            </legend>
            {methods.data.map((method) => (
              <label
                key={method.carrierCode}
                className="has-checked:border-brand-600 flex items-center gap-3 rounded-lg border border-stone-300 bg-white px-3 py-2"
              >
                <input
                  type="radio"
                  value={method.carrierCode}
                  {...register('carrierCode', {
                    onChange: () => setValue('pickupPointId', ''),
                  })}
                />
                {t.methodOption(
                  method.name,
                  method.price.amount === 0
                    ? t.free
                    : formatMoney(method.price),
                )}
              </label>
            ))}
            {errors.carrierCode && (
              <p className="text-sm text-red-700">
                {errors.carrierCode.message}
              </p>
            )}
          </fieldset>
        ))}

      {country &&
        carrierCode &&
        (points.isPending ? (
          <div role="status" aria-label={t.loadingPoints}>
            <Skeleton className="h-28" />
          </div>
        ) : points.isError ? (
          <ErrorState
            message={t.pointsError}
            onRetry={() => points.refetch()}
          />
        ) : (
          <ParcelMachinePicker
            points={points.data}
            value={pickupPointId}
            onChange={(id) =>
              setValue('pickupPointId', id, { shouldValidate: true })
            }
            error={errors.pickupPointId?.message}
          />
        ))}

      {/* Registered so validation errors show even before a carrier is picked. */}
      <input type="hidden" {...register('pickupPointId')} />
      {!carrierCode && errors.pickupPointId && country && (
        <p className="text-sm text-red-700">{errors.pickupPointId.message}</p>
      )}
    </div>
  )
}
