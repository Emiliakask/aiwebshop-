package com.webshop.auth.smartid;

/** A Smart-ID login could not be started or verified. {@code reason} is safe to show (no personal data). */
public class SmartIdException extends RuntimeException {

	public enum Reason {
		NOT_FOUND, UNAVAILABLE, INVALID_RESPONSE
	}

	private final Reason reason;

	public SmartIdException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public SmartIdException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
