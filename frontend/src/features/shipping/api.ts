import { useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/apiClient'
import { pickupPointSchema, shippingMethodSchema } from './types'

export const shippingKeys = {
  all: ['shipping'] as const,
  methods: (country: string) =>
    [...shippingKeys.all, 'methods', country] as const,
  pickupPoints: (carrier: string, country: string) =>
    [...shippingKeys.all, 'pickup-points', carrier, country] as const,
}

/** Delivery options for a country. The backend prices them for this session's cart. */
export function useShippingMethods(country: string | undefined) {
  return useQuery({
    queryKey: shippingKeys.methods(country ?? ''),
    queryFn: ({ signal }) =>
      apiRequest(
        `/shipping/methods?country=${encodeURIComponent(country ?? '')}`,
        z.array(shippingMethodSchema),
        { signal },
      ),
    enabled: Boolean(country),
  })
}

/** Parcel machines for a carrier and country. The list changes rarely, so cache it for a while. */
export function usePickupPoints(
  carrier: string | undefined,
  country: string | undefined,
) {
  return useQuery({
    queryKey: shippingKeys.pickupPoints(carrier ?? '', country ?? ''),
    queryFn: ({ signal }) =>
      apiRequest(
        `/shipping/pickup-points?carrier=${encodeURIComponent(carrier ?? '')}&country=${encodeURIComponent(country ?? '')}`,
        z.array(pickupPointSchema),
        { signal },
      ),
    enabled: Boolean(carrier && country),
    staleTime: 60 * 60 * 1000,
  })
}
