import { useId, useMemo, useState } from 'react'
import { inputClasses } from '@/components/ui/FormField'
import { strings } from '@/lib/i18n/strings'
import { matchesSearch, type PickupPoint } from '../types'

const t = strings.shipping

type ParcelMachinePickerProps = {
  points: PickupPoint[]
  value: string
  onChange: (pickupPointId: string) => void
  error?: string
}

/**
 * Search box + native select, grouped by city. A native select works with
 * keyboards, screen readers and phone pickers without extra code.
 */
export function ParcelMachinePicker({
  points,
  value,
  onChange,
  error,
}: ParcelMachinePickerProps) {
  const searchId = useId()
  const selectId = useId()
  const errorId = useId()
  const [search, setSearch] = useState('')

  const byCity = useMemo(() => {
    const visible = points.filter(
      (point) => point.id === value || matchesSearch(point, search),
    )
    const groups = new Map<string, PickupPoint[]>()
    for (const point of visible) {
      groups.set(point.city, [...(groups.get(point.city) ?? []), point])
    }
    return groups
  }, [points, search, value])

  const matchCount = [...byCity.values()].reduce((n, g) => n + g.length, 0)
  const selected = points.find((point) => point.id === value)

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-1.5">
        <label
          htmlFor={searchId}
          className="text-sm font-medium text-stone-800"
        >
          {t.searchLabel}
        </label>
        <input
          id={searchId}
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t.searchPlaceholder}
          className={inputClasses}
        />
        <p className="text-sm text-stone-600" aria-live="polite">
          {t.matches(matchCount)}
        </p>
      </div>

      <div className="flex flex-col gap-1.5">
        <label
          htmlFor={selectId}
          className="text-sm font-medium text-stone-800"
        >
          {t.pickupPointLabel}
        </label>
        <select
          id={selectId}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? errorId : undefined}
          className={inputClasses}
        >
          <option value="">{t.choosePickupPoint}</option>
          {[...byCity].map(([city, cityPoints]) => (
            <optgroup key={city} label={city}>
              {cityPoints.map((point) => (
                <option key={point.id} value={point.id}>
                  {point.address
                    ? `${point.name}, ${point.address}`
                    : point.name}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
        {error && (
          <p id={errorId} className="text-sm text-red-700">
            {error}
          </p>
        )}
        {selected && (
          <p className="text-sm text-stone-700">
            {t.selected(
              selected.name,
              [selected.address, selected.city].filter(Boolean).join(', '),
            )}
          </p>
        )}
      </div>
    </div>
  )
}
