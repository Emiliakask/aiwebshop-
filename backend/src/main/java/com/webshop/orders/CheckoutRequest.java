package com.webshop.orders;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the checkout form sends: contact and delivery details only. Products, quantities and prices
 * come from the server-side cart. Same rules as checkoutFormSchema in the frontend.
 */
public record CheckoutRequest(
		@NotBlank @Email @Size(max = 200) String email,
		@NotBlank @Size(max = 200) String fullName,
		@NotBlank @Size(max = 200) String addressLine1,
		@NotBlank @Size(max = 200) String city,
		@NotNull @Pattern(regexp = "[A-Za-z0-9 -]{3,10}") String postalCode,
		@NotNull @Pattern(regexp = "EE|FI|LV|LT|SE|DE") String country) {
}
