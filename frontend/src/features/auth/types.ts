import { z } from 'zod'

/** Who is logged in. Only admins for now; Smart-ID customers come later (docs/auth.md). */
export const meSchema = z.object({
  type: z.literal('admin'),
  name: z.string(),
  email: z.string(),
})

export type Me = z.infer<typeof meSchema>
