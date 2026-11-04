package com.webshop.orders;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Same values as orderStatusSchema in frontend/src/features/checkout/types.ts. */
public enum OrderStatus {
	@JsonProperty("pending_payment")
	PENDING_PAYMENT,
	@JsonProperty("paid")
	PAID,
	@JsonProperty("payment_failed")
	PAYMENT_FAILED,
	@JsonProperty("cancelled")
	CANCELLED
}
