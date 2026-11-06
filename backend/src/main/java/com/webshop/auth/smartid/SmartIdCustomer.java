package com.webshop.auth.smartid;

import java.io.Serial;
import java.io.Serializable;

/**
 * A logged-in customer. Comes only from a verified Smart-ID certificate (or the demo test account).
 * The personal code is sensitive: never log it, and show it only through {@link #maskedPersonalCode()}.
 */
public record SmartIdCustomer(String country, String personalCode, String firstName, String lastName,
		boolean testAccount) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final String ROLE = "ROLE_CUSTOMER";

	public String fullName() {
		return (firstName + " " + lastName).trim();
	}

	/** "40504040001" -> "4050404****". */
	public String maskedPersonalCode() {
		int visible = Math.max(0, personalCode.length() - 4);
		return personalCode.substring(0, visible) + "*".repeat(personalCode.length() - visible);
	}

	@Override
	public String toString() {
		return "SmartIdCustomer[" + country + ", " + maskedPersonalCode() + "]";
	}
}
