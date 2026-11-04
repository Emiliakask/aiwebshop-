import type { ComponentProps } from 'react'
import { Link } from 'react-router'
import { cn } from '@/lib/cn'

type Variant = 'primary' | 'secondary' | 'ghost'
type Size = 'md' | 'lg'

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 disabled:cursor-not-allowed disabled:opacity-50'

const variants: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700',
  secondary:
    'border border-stone-300 bg-white text-stone-900 hover:bg-stone-50',
  ghost: 'text-stone-700 hover:bg-stone-100 hover:text-stone-900',
}

const sizes: Record<Size, string> = {
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3 text-base',
}

type StyleProps = { variant?: Variant; size?: Size }

function buttonClasses({ variant = 'primary', size = 'md' }: StyleProps) {
  return cn(base, variants[variant], sizes[size])
}

type ButtonProps = ComponentProps<'button'> & StyleProps

export function Button({
  variant,
  size,
  className,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cn(buttonClasses({ variant, size }), className)}
      {...props}
    />
  )
}

type ButtonLinkProps = ComponentProps<typeof Link> & StyleProps

/** A link that looks like a button, for navigation actions. */
export function ButtonLink({
  variant,
  size,
  className,
  ...props
}: ButtonLinkProps) {
  return (
    <Link
      className={cn(buttonClasses({ variant, size }), className)}
      {...props}
    />
  )
}
