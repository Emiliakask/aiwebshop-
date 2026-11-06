package com.webshop.auth.smartid;

import java.io.Serial;
import java.io.Serializable;

/**
 * A started Smart-ID login, kept in the server session until SK answers. Never sent to the browser.
 *
 * @param expectedIdentity "PNOEE-40504040001": the certificate must belong to the person who was asked
 */
public record PendingLogin(String sessionId, String rpChallenge, String interactions, String hashAlgorithm,
		String flowType, String expectedIdentity) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final String SESSION_ATTRIBUTE = PendingLogin.class.getName();

	@Override
	public String toString() {
		return "PendingLogin[" + sessionId + "]";
	}
}
