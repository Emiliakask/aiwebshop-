package com.webshop.auth.smartid;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real response from SK's demo service: choose-number login of demo account EE 40504040001 ("OK TEST"),
 * recorded on 2026-11-06 with display text "Log in to Webshop". Its certificate is valid until 2029-05-18.
 */
final class SmartIdFixture {

	static final String IDENTITY = "PNOEE-40504040001";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	final String rpChallenge;
	final String interactions;
	final JsonNode statusJson;

	private SmartIdFixture() {
		try (InputStream in = SmartIdFixture.class.getResourceAsStream("/smartid/demo-choose-number-ok.json")) {
			JsonNode root = JSON.readTree(in);
			rpChallenge = root.get("rpChallenge").asString();
			interactions = root.get("interactions").asString();
			statusJson = root.get("status");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static SmartIdFixture load() {
		return new SmartIdFixture();
	}

	byte[] challengeBytes() {
		return Base64.getDecoder().decode(rpChallenge);
	}

	SessionStatus status() {
		return JSON.treeToValue(statusJson, SessionStatus.class);
	}

	PendingLogin pending() {
		return new PendingLogin("session-1", rpChallenge, interactions, "SHA3-512", "Notification", IDENTITY);
	}

	static SmartIdSettings demoSettings() {
		return new SmartIdSettings("demo", "", "", "", false, false, "Log in to Webshop");
	}
}
