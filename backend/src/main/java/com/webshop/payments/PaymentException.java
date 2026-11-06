package com.webshop.payments;

/** The provider could not be reached or refused the request. Message is safe to log (no secrets). */
public class PaymentException extends RuntimeException {

	public PaymentException(String message) {
		super(message);
	}

	public PaymentException(String message, Throwable cause) {
		super(message, cause);
	}
}
