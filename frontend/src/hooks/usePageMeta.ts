import { useEffect } from 'react'
import { strings } from '@/lib/i18n/strings'

/** Sets the document title and meta description for the current page. */
export function usePageMeta(title: string | undefined, description?: string) {
  useEffect(() => {
    document.title = title ? `${title} · ${strings.app.name}` : strings.app.name
  }, [title])

  useEffect(() => {
    if (!description) return
    let meta = document.querySelector<HTMLMetaElement>(
      'meta[name="description"]',
    )
    if (!meta) {
      meta = document.createElement('meta')
      meta.name = 'description'
      document.head.append(meta)
    }
    meta.content = description
  }, [description])
}
