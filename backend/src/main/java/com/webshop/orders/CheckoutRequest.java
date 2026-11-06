package com.webshop.orders;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the checkout form sends: contact details and the chosen parcel machine. Products, quantities
 * and all prices come from the server. Same rules as checkoutFormSchema in the frontend.
 */
public record CheckoutRequest(
		@NotBlank @Email @Size(max = 200) String email,
		@NotBlank @Size(max = 200) String fullName,
		/** The carrier texts the pickup code to this number. */
		@NotNull @Pattern(regexp = "\\+?[0-9][0-9 ]{6,19}") String phone,
		@NotNull @Pattern(regexp = "EE|LV|LT") String country,
		@NotBlank @Size(max = 50) String carrierCode,
		@NotBlank @Size(max = 50) String pickupPointId) {
}
