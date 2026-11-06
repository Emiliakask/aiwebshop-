package com.webshop.auth.smartid;

/** Response of SK GET /session/{sessionID} (RP API v3). Only the fields we use. */
public record SessionStatus(String state, Result result, String signatureProtocol, Signature signature, Cert cert,
		String interactionTypeUsed) {

	public record Result(String endResult, String documentNumber) {
	}

	public record Signature(String value, String serverRandom, String userChallenge, String flowType,
			String signatureAlgorithm, SignatureAlgorithmParameters signatureAlgorithmParameters) {
	}

	public record SignatureAlgorithmParameters(String hashAlgorithm, MaskGenAlgorithm maskGenAlgorithm,
			Integer saltLength, String trailerField) {
	}

	public record MaskGenAlgorithm(String algorithm, MgfParameters parameters) {
	}

	public record MgfParameters(String hashAlgorithm) {
	}

	public record Cert(String value, String certificateLevel) {
	}

	public boolean running() {
		return "RUNNING".equals(state);
	}
}
