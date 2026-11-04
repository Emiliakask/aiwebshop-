import { useId, type ReactElement } from 'react'

type ControlProps = {
  id: string
  'aria-invalid': boolean
  'aria-describedby': string | undefined
}

type FormFieldProps = {
  label: string
  error?: string
  /** Render the input/select, spreading the given props onto it. */
  children: (props: ControlProps) => ReactElement
}

/** Input styling without a width, for controls that size themselves. */
export const inputBaseClasses =
  'rounded-lg border border-stone-300 bg-white px-3 py-2 text-stone-900 shadow-xs focus-visible:border-brand-600 focus-visible:outline-2 focus-visible:outline-brand-600/30 aria-invalid:border-red-500'

export const inputClasses = `${inputBaseClasses} w-full`

/** Label + control + error message, wired up for screen readers. */
export function FormField({ label, error, children }: FormFieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-stone-800">
        {label}
      </label>
      {children({
        id,
        'aria-invalid': Boolean(error),
        'aria-describedby': error ? errorId : undefined,
      })}
      {error && (
        <p id={errorId} className="text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  )
}
