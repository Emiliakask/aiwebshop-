package com.webshop.auth.smartid;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** Fresh random rpChallenge bytes for every login. A bean so tests can replay a recorded SK response. */
@Component
public class SmartIdChallenges {

	private final SecureRandom random = new SecureRandom();

	public byte[] next() {
		byte[] challenge = new byte[64];
		random.nextBytes(challenge);
		return challenge;
	}
}
