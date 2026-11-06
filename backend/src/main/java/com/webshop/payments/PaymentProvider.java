package com.webshop.payments;

import com.webshop.common.Money;

/**
 * A payment provider plugin (docs/payments.md). Business code only uses this interface, so adding
 * Montonio, Stripe, ... means adding one more implementation.
 */
public interface PaymentProvider {

	/** Stable id used in config, URLs and the database: "everypay", "montonio", ... */
	String code();

	String displayName();

	/** Creates the payment at the provider. The customer is sent to {@link Started#redirectUrl()}. */
	Started start(Request request);

	/** Asks the provider directly. The only source of truth for "paid"; never the return URL. */
	Result fetchStatus(String providerReference);

	/**
	 * @param returnUrl where the provider sends the customer back to (our backend, which then checks the status)
	 */
	record Request(String orderId, Money amount, String email, String returnUrl) {
	}

	record Started(String providerReference, String redirectUrl) {
	}

	/** @param amount what the provider says was charged; must equal the order total before we accept PAID */
	record Result(String providerReference, PaymentStatus status, Money amount, String providerState) {
	}
}
