package com.webshop.shipping;

/**
 * A parcel machine (pakiautomaat). Same shape as pickupPointSchema in
 * frontend/src/features/shipping/types.ts.
 *
 * @param id carrier's own id for the machine (for Omniva: the ZIP field)
 */
public record PickupPoint(String id, String carrierCode, String name, String address, String city,
		String countryCode, Double latitude, Double longitude) {
}
