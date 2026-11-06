package com.webshop.shipping;

import java.util.List;
import java.util.Set;

import com.webshop.common.Money;

/**
 * One parcel machine company (Omniva, SmartPost, DPD, Unisend, ...). Checkout and orders only use
 * this interface; adding a carrier means adding an implementation plus config (docs/shipping.md).
 */
public interface ShippingCarrier {

	/** Stable id stored on orders and used in URLs and config: "omniva", "smartpost", ... */
	String code();

	String displayName();

	/** ISO country codes this carrier delivers to. */
	Set<String> countries();

	/** Parcel machines in a country. Implementations cache the carrier's list. */
	List<PickupPoint> pickupPoints(String countryCode);

	/** Delivery price before the shop's free-shipping rule is applied. */
	Money basePrice();
}
